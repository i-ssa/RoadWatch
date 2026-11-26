package com.example.roadwatch.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.ui.dashboard.DashboardScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoScanTest {
    @get:Rule val compose = createComposeRule()

    @Test fun galleryMediaScanOpensFromHomeWithoutStartingCamera() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("roadwatch_ui", Context.MODE_PRIVATE)
            .edit().putBoolean("intro_v1", true).commit()
        compose.setContent { DashboardScreen(onBack = {}, owner = "gallery-route-test") }
        compose.onNodeWithText("Upload photo or video").performClick()
        compose.onNodeWithText("Choose photo or video").assertIsDisplayed()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
    }

    @Test fun rejectedPhotoCandidateIsOmittedFromResults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = UUID.randomUUID().toString()
        val store = VideoScanStore(context, owner)
        val directory = store.newScanDirectory()
        try {
            val candidate = VideoCandidate(
                0, Detection(0.1f, 0.1f, 0.5f, 0.5f, 0.9f), File(directory, "0.jpg"),
            )
            store.save(
                SavedVideoScan(
                    directory, listOf(candidate),
                    verdicts = mapOf(0 to VideoVerdict("rejected", "No road surface")),
                    isPhoto = true,
                ),
            )
            compose.setContent { VideoScanScreen(owner, onBack = {}) }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("No potholes confirmed").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("No potholes confirmed").assertIsDisplayed()
            compose.onNodeWithText("Model candidates", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Save draft").assertDoesNotExist()
        } finally {
            directory.parentFile?.deleteRecursively()
        }
    }

    @Test fun solidColorPhotoKeepsPreviewWithoutCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File.createTempFile("blank-photo", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        try {
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally {
            bitmap.recycle()
        }
        val store = VideoScanStore(context, UUID.randomUUID().toString())
        val directory = store.newScanDirectory()
        try {
            val scan = runBlocking { scanUploadedPhoto(context, Uri.fromFile(input), directory) }
            store.save(scan)
            val restored = checkNotNull(store.restore())
            assertTrue(restored.isPhoto)
            assertEquals(0, restored.candidates.size)
            assertTrue(File(restored.directory, "0.jpg").isFile)
        } finally {
            input.delete()
            directory.parentFile?.deleteRecursively()
        }
    }
}
