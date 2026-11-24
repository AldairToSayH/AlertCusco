package com.example.evaluacin_consolidado2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {
    @Test
    fun pocketArmsAfterThreeSecondsAndRequiresThreeExtractionSignals() {
        val detector = DetectionEngine()
        detector.onProximity(true, 1_000L)
        detector.onLight(2f, 1_100L)
        detector.tick(3_900L)
        assertFalse(detector.pocketArmed)
        detector.tick(4_100L)
        assertTrue(detector.pocketArmed)

        detector.onAcceleration(0f, 0f, 9f, 4_200L)
        assertNull(detector.onProximity(false, 4_300L))
        assertNull(detector.onLight(30f, 4_400L))
        assertEquals(DetectionEngine.Trigger.EXTRACTION, detector.onAcceleration(20f, 0f, 9f, 4_500L))
    }

    @Test
    fun ordinaryPocketRemovalDoesNotTriggerWithoutJerk() {
        val detector = DetectionEngine()
        detector.onProximity(true, 1_000L)
        detector.onLight(1f, 1_100L)
        detector.tick(4_200L)
        assertNull(detector.onProximity(false, 4_300L))
        assertNull(detector.onLight(40f, 4_400L))
        detector.tick(6_500L)
        assertFalse(detector.pocketArmed)
        assertNull(detector.onAcceleration(0f, 0f, 9f, 6_600L))
    }

    @Test
    fun threeDistinctShakesTriggerSosEvenWithProximityCovered() {
        val detector = DetectionEngine()
        detector.onProximity(true, 900L)
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        assertNull(detector.onAcceleration(20f, 0f, 9f, 1_300L))
        assertEquals(1, detector.shakeCount)
        assertNull(detector.onAcceleration(0f, 0f, 9f, 1_600L))
        assertNull(detector.onAcceleration(20f, 0f, 9f, 1_900L))
        assertEquals(2, detector.shakeCount)
        assertNull(detector.onAcceleration(0f, 0f, 9f, 2_200L))
        assertEquals(DetectionEngine.Trigger.SOS, detector.onAcceleration(20f, 0f, 9f, 2_500L))
    }

    @Test
    fun continuousSingleMovementDoesNotCountAsThreeShakes() {
        val detector = DetectionEngine()
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        detector.onAcceleration(20f, 0f, 9f, 1_300L)
        detector.onAcceleration(20f, 0f, 9f, 1_700L)
        detector.onAcceleration(20f, 0f, 9f, 2_100L)
        assertEquals(1, detector.shakeCount)
    }

    @Test
    fun oneAbruptSnatchWhileExposedTriggersAfterGestureWindow() {
        val detector = DetectionEngine()
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        assertNull(detector.onAcceleration(35f, 0f, 9f, 1_300L))
        assertNull(detector.tick(2_499L))
        assertEquals(DetectionEngine.Trigger.SNATCH, detector.tick(2_500L))
    }

    @Test
    fun twoStrongPullsTriggerSnatchWhenThereIsNoThirdShake() {
        val detector = DetectionEngine()
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        assertNull(detector.onAcceleration(24f, 0f, 9f, 1_300L))
        assertNull(detector.onAcceleration(0f, 0f, 9f, 1_600L))
        assertNull(detector.onAcceleration(24f, 0f, 9f, 1_900L))
        assertEquals(DetectionEngine.Trigger.SNATCH, detector.tick(3_100L))
    }

    @Test
    fun threeShakesTakePriorityOverSnatch() {
        val detector = DetectionEngine()
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        assertNull(detector.onAcceleration(35f, 0f, 9f, 1_300L))
        assertNull(detector.onAcceleration(0f, 0f, 9f, 1_600L))
        assertNull(detector.onAcceleration(35f, 0f, 9f, 1_900L))
        assertNull(detector.onAcceleration(0f, 0f, 9f, 2_200L))
        assertEquals(DetectionEngine.Trigger.SOS, detector.onAcceleration(35f, 0f, 9f, 2_500L))
    }

    @Test
    fun ordinaryMovementDoesNotTriggerSnatch() {
        val detector = DetectionEngine()
        detector.onAcceleration(0f, 0f, 9f, 1_000L)
        assertNull(detector.onAcceleration(10f, 0f, 9f, 1_300L))
        assertNull(detector.tick(3_000L))
    }
}
