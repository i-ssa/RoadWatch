package com.example.roadwatch

import com.example.roadwatch.ui.scan.Detection
import com.example.roadwatch.ui.scan.RetainedDetection
import com.example.roadwatch.ui.scan.Verdict
import com.example.roadwatch.ui.scan.retainDetections
import org.junit.Assert.assertEquals
import org.junit.Test

class PotholeVerificationTest {
    private val boxes = List(4) { Detection(it / 10f, 0.1f, 0.1f, 0.1f, 0.8f) }

    @Test fun usesOrderedVerdictsAndKeepsUncheckedBoxesUnverified() {
        val results = retainDetections(boxes, listOf(Verdict.REJECTED, Verdict.CONFIRMED, Verdict.UNCERTAIN))
        assertEquals(listOf(
            RetainedDetection(boxes[1], true),
            RetainedDetection(boxes[2], false),
            RetainedDetection(boxes[3], false)
        ), results)
    }

    @Test fun retainsOfflineDetectionsAsUnverified() {
        assertEquals(boxes.map { RetainedDetection(it, false) }, retainDetections(boxes, emptyList()))
    }

    @Test fun dropsCaptureWhenEveryCandidateIsRejected() {
        assertEquals(emptyList<RetainedDetection>(), retainDetections(listOf(boxes[0]), listOf(Verdict.REJECTED)))
    }
}
