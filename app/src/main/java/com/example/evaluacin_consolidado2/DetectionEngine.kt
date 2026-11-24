package com.example.evaluacin_consolidado2

import kotlin.math.sqrt

/** All times use elapsed realtime milliseconds, not wall clock time. */
internal class DetectionEngine {
    var near = false
        private set
    var lux: Float? = null
        private set
    var pocketArmed = false
        private set
    var motion = 0f
        private set

    private var pocketSince = 0L
    private var pocketLux = 0f
    private var extractionSince = 0L
    private var sawFar = false
    private var sawLight = false
    private var sawJerk = false
    private var gravity: FloatArray? = null
    var shakeCount = 0
        private set
    private var lastShake = 0L
    private var shakeReady = true
    private var snatchFirst = 0L
    private var snatchLast = 0L
    private var snatchHits = 0
    private var snatchCandidate = false

    fun onProximity(isNear: Boolean, now: Long): Trigger? {
        if (near && !isNear && pocketArmed) {
            beginExtraction(now)
            sawFar = true
        }
        near = isNear
        updatePocket(now)
        return evaluateExtraction(now)
    }

    fun onLight(value: Float, now: Long): Trigger? {
        lux = value
        if (pocketArmed && value >= maxOf(18f, pocketLux + 12f)) {
            beginExtraction(now)
            sawLight = true
        }
        updatePocket(now)
        return evaluateExtraction(now)
    }

    fun onAcceleration(x: Float, y: Float, z: Float, now: Long): Trigger? {
        val filteredGravity = gravity ?: floatArrayOf(x, y, z).also { gravity = it }
        filteredGravity[0] = 0.95f * filteredGravity[0] + 0.05f * x
        filteredGravity[1] = 0.95f * filteredGravity[1] + 0.05f * y
        filteredGravity[2] = 0.95f * filteredGravity[2] + 0.05f * z
        val dx = x - filteredGravity[0]
        val dy = y - filteredGravity[1]
        val dz = z - filteredGravity[2]
        motion = sqrt(dx * dx + dy * dy + dz * dz)

        if (pocketArmed && motion >= 12f) {
            beginExtraction(now)
            sawJerk = true
        }
        evaluateExtraction(now)?.let { return it }

        if (shakeCount > 0 && now - lastShake > 3000L) shakeCount = 0
        if (motion <= 5f) shakeReady = true
        if (shakeReady && motion >= 8.5f && (lastShake == 0L || now - lastShake >= 300L)) {
            shakeReady = false
            shakeCount = if (lastShake != 0L && now - lastShake <= 3000L) shakeCount + 1 else 1
            lastShake = now
            if (!pocketArmed && motion >= 19f) {
                if (snatchFirst == 0L || now - snatchLast > 850L) {
                    snatchFirst = now
                    snatchHits = 1
                } else snatchHits++
                snatchLast = now
                if (motion >= 27f || snatchHits >= 2) snatchCandidate = true
            }
            if (shakeCount >= 3) {
                reset()
                return Trigger.SOS
            }
        }
        return null
    }

    fun tick(now: Long): Trigger? {
        updatePocket(now)
        evaluateExtraction(now)?.let { return it }
        if (snatchCandidate && lastShake != 0L && now - lastShake >= 1200L) {
            reset()
            return Trigger.SNATCH
        }
        return null
    }

    fun reset() {
        pocketArmed = false
        pocketSince = 0L
        extractionSince = 0L
        sawFar = false
        sawLight = false
        sawJerk = false
        shakeCount = 0
        lastShake = 0L
        shakeReady = true
        gravity = null
        snatchFirst = 0L
        snatchLast = 0L
        snatchHits = 0
        snatchCandidate = false
    }

    private fun updatePocket(now: Long) {
        if (near && (lux ?: Float.MAX_VALUE) <= 10f) {
            if (pocketSince == 0L) pocketSince = now
            if (!pocketArmed && now - pocketSince >= 3000L) {
                pocketArmed = true
                pocketLux = lux ?: 0f
            }
        } else if (!pocketArmed) {
            pocketSince = 0L
        }
    }

    private fun beginExtraction(now: Long) {
        if (extractionSince == 0L || now - extractionSince > 1800L) {
            extractionSince = now
            sawFar = false
            sawLight = false
            sawJerk = false
        }
    }

    private fun evaluateExtraction(now: Long): Trigger? {
        if (extractionSince != 0L && now - extractionSince > 1800L) {
            extractionSince = 0L
            sawFar = false
            sawLight = false
            sawJerk = false
            if (!near || (lux ?: 0f) > 10f) {
                pocketArmed = false
                pocketSince = 0L
            }
        }
        if (pocketArmed && sawFar && sawLight && sawJerk) {
            reset()
            return Trigger.EXTRACTION
        }
        return null
    }

    enum class Trigger { EXTRACTION, SNATCH, SOS }
}
