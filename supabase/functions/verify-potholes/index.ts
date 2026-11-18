import { createHandler } from "./verify.mjs"

Deno.serve(createHandler({
  url: Deno.env.get("SUPABASE_URL"),
  serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
  geminiKey: Deno.env.get("GEMINI_API_KEY"),
  model: Deno.env.get("GEMINI_MODEL") || "gemini-3.1-flash-lite",
}))
