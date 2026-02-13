package com.bitchat.android.mesh

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bitchat.android.util.AppConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerManagerInstrumentationTests {
    private lateinit var powerManager: PowerManager

    @Before
    fun setUp() {
        powerManager = PowerManager(ApplicationProvider.getApplicationContext())
        powerManager.stop()
    }

    @After
    fun tearDown() {
        powerManager.stop()
    }

    @Test
    fun performanceModeWhenChargingForeground() {
        setPrivateField("batteryLevel", 90)
        setPrivateField("isCharging", true)
        setPrivateField("isAppInBackground", false)
        setPrivateField("currentMode", PowerManager.PowerMode.POWER_SAVER)

        updatePowerMode()

        assertMode(PowerManager.PowerMode.PERFORMANCE)
        assertFalse(powerManager.shouldUseDutyCycle())
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, powerManager.getScanSettings().scanMode)
        assertEquals(
            AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            powerManager.getAdvertiseSettings().txPowerLevel
        )
    }

    @Test
    fun ultraLowModeWhenBatteryIsCritical() {
        setPrivateField("batteryLevel", AppConstants.Power.CRITICAL_BATTERY_PERCENT)
        setPrivateField("isCharging", false)
        setPrivateField("isAppInBackground", true)

        updatePowerMode()

        assertMode(PowerManager.PowerMode.ULTRA_LOW_POWER)
        assertEquals(AppConstants.Power.MAX_CONNECTIONS_ULTRA_LOW, powerManager.getMaxConnections())
        assertEquals(-65, powerManager.getRSSIThreshold())
    }

    @Test
    fun powerSaverModeWhenLowBatteryInBackground() {
        setPrivateField("batteryLevel", AppConstants.Power.LOW_BATTERY_PERCENT)
        setPrivateField("isCharging", false)
        setPrivateField("isAppInBackground", true)

        updatePowerMode()

        assertMode(PowerManager.PowerMode.POWER_SAVER)
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, powerManager.getScanSettings().scanMode)
        assertEquals(-75, powerManager.getRSSIThreshold())
    }

    private fun setPrivateField(name: String, value: Any) {
        val field = PowerManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(powerManager, value)
    }

    private fun updatePowerMode() {
        val method = PowerManager::class.java.getDeclaredMethod("updatePowerMode")
        method.isAccessible = true
        method.invoke(powerManager)
    }

    private fun assertMode(expected: PowerManager.PowerMode) {
        val info = powerManager.getPowerInfo()
        assertTrue(info.contains("Current Mode: $expected"))
    }
}
