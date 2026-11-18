import assert from "node:assert/strict"
import test from "node:test"
import { createHandler } from "./verify.mjs"

const frame = "/9j/AA=="
const candidate = { box: [0.1, 0.2, 0.3, 0.4], crop: "/9j/AQ==" }
const account = "11111111-1111-1111-1111-111111111111"
const project = "https://project.example"
const model = "gemini-3.1-flash-lite"

function request(body = { frame, candidates: [candidate] }, authorization = "Bearer signed-in") {
  return new Request(`${project}/functions/v1/verify-potholes`, {
    method: "POST",
    headers: { Authorization: authorization },
    body: JSON.stringify(body),
  })
}

function fixture({ decisions = ["confirmed"], authStatus = 200, quota = true,
  upstreamStatus = 200, finishReason = "STOP", timeoutMs, waitForTimeout } = {}) {
  const calls = []
  const handler = createHandler({
    url: project, serviceKey: "server-key", geminiKey: "gemini-key", model, timeoutMs,
    fetcher: async (url, options) => {
      calls.push({ url, options })
      if (url.endsWith("/auth/v1/user")) return Response.json({ id: account }, { status: authStatus })
      if (url.endsWith("/rest/v1/rpc/take_pothole_verification_slot")) return Response.json(quota)
      if (waitForTimeout) {
        // Hold a timer because AbortSignal.timeout alone does not keep Node alive.
        const timer = setTimeout(() => {}, 1000)
        try {
          await new Promise((_, reject) => {
            options.signal.addEventListener("abort", () => reject(options.signal.reason), { once: true })
          })
        } finally {
          clearTimeout(timer)
        }
      }
      return Response.json({
        candidates: [{ finishReason, content: { parts: [{ text: JSON.stringify(decisions) }] } }],
      }, { status: upstreamStatus, headers: { "Retry-After": "90" } })
    },
  })
  return { calls, handler }
}

test("batches ordered verdicts and sends the full frame and target crops in one Gemini call", async () => {
  const verdicts = ["confirmed", "rejected", "uncertain"]
  const { calls, handler } = fixture({ decisions: verdicts })
  const response = await handler(request({ frame, candidates: [candidate, candidate, candidate] }))
  assert.equal(response.status, 200)
  assert.deepEqual(await response.json(), { verdicts })
  assert.equal(calls.length, 3)
  assert.equal(calls[0].options.headers.Authorization, "Bearer signed-in")
  assert.deepEqual(JSON.parse(calls[1].options.body), { account_id: account })
  assert.equal(calls[2].options.headers["x-goog-api-key"], "gemini-key")
  assert.ok(!calls[2].url.includes("gemini-key"))
  const { contents, generationConfig } = JSON.parse(calls[2].options.body)
  assert.deepEqual(contents[0].parts.flatMap(p => p.inlineData ? [p.inlineData.data] : []),
    [frame, candidate.crop, candidate.crop, candidate.crop])
  assert.equal(generationConfig.responseSchema.maxItems, 3)
})

test("rejects missing authorization and invalid or empty candidates without upstream calls", async () => {
  const { calls, handler } = fixture()
  assert.equal((await handler(request(undefined, ""))).status, 401)
  assert.equal((await handler(request({ frame, candidates: [] }))).status, 400)
  assert.equal((await handler(request({ frame, candidates: [{ ...candidate, box: [0.9, 0.1, 0.3, 0.2] }] }))).status, 400)
  assert.equal((await handler(request({ frame: "x".repeat(950_000), candidates: [candidate] }))).status, 400)
  assert.equal(calls.length, 0)
})

test("rejects expired sessions before using quota or invoking Gemini", async () => {
  const { calls, handler } = fixture({ authStatus: 403 })
  assert.equal((await handler(request())).status, 401)
  assert.equal(calls.length, 1)
})

test("enforces the shared quota before invoking Gemini", async () => {
  const { calls, handler } = fixture({ quota: false })
  const response = await handler(request())
  assert.equal(response.status, 429)
  assert.equal(response.headers.get("Retry-After"), "20")
  assert.equal(calls.length, 2)
})

test("propagates upstream rate limits without retrying", async () => {
  const { calls, handler } = fixture({ upstreamStatus: 429 })
  const response = await handler(request())
  assert.equal(response.status, 429)
  assert.equal(response.headers.get("Retry-After"), "90")
  assert.equal(calls.length, 3)
})

test("returns no decision for blocked, incomplete, or unknown verdicts", async () => {
  for (const options of [{ finishReason: "SAFETY" }, { decisions: [] }, { decisions: ["maybe"] }]) {
    const { handler } = fixture(options)
    assert.equal((await handler(request())).status, 502)
  }
})

test("bounds provider latency without retrying", async () => {
  const { calls, handler } = fixture({ timeoutMs: 30, waitForTimeout: true })
  assert.equal((await handler(request())).status, 504)
  assert.equal(calls.length, 3)
})

test("handles provider outages without marking any candidate as rejected", async () => {
  const { handler } = fixture({ upstreamStatus: 503 })
  const response = await handler(request())
  assert.equal(response.status, 503)
  assert.deepEqual(await response.json(), { error: "unavailable" })
})
