package com.example.roadwatch

import com.example.roadwatch.ui.scan.Detection
import com.example.roadwatch.ui.scan.PotholeDetector
import org.junit.Assert.*
import org.junit.Test

class DetectionTest {
    @Test fun overlappingBoxesAreSuppressedButSeparatePotholesRemain() {
        val first=Detection(.1f,.1f,.2f,.2f,.9f)
        val duplicate=Detection(.11f,.11f,.2f,.2f,.8f)
        val separate=Detection(.7f,.7f,.1f,.1f,.7f)
        assertEquals(listOf(first,separate),PotholeDetector.suppress(listOf(first,duplicate,separate)))
    }
}
