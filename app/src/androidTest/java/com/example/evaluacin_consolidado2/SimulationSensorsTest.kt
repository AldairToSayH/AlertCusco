package com.example.evaluacin_consolidado2

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationSensorsTest {
    @Test
    fun simulationStartsSensorsWithoutContactAndStopsCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        try {
            ProtectionService.command(context, ProtectionService.ACTION_START, true)
            waitUntil { ProtectionState.snapshot.active }
            assertTrue(ProtectionState.snapshot.testMode)
            waitUntil { ProtectionState.snapshot.lux != null }
            assertNotEquals("Desactivado", ProtectionState.snapshot.phase)
        } finally {
            ProtectionService.command(context, ProtectionService.ACTION_STOP)
            waitUntil { !ProtectionState.snapshot.active }
            assertFalse(ProtectionState.snapshot.active)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("No llegó el estado esperado en 5 segundos: ${ProtectionState.snapshot}")
    }
}
