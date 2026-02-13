package com.bitchat.android.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothConnectionTrackerInstrumentationTests {
    private lateinit var tracker: BluetoothConnectionTracker
    private lateinit var powerManager: PowerManager

    @Before
    fun setUp() {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        powerManager = PowerManager(ApplicationProvider.getApplicationContext())
        tracker = BluetoothConnectionTracker(scope, powerManager)
    }

    @After
    fun tearDown() {
        powerManager.stop()
    }

    @Test
    fun addPendingConnection_blocksImmediateRetry() {
        val address = "00:11:22:33:44:55"

        assertTrue(tracker.addPendingConnection(address))
        assertFalse(tracker.isConnectionAttemptAllowed(address))
    }

    @Test
    fun bestRssiPrefersConnectionRssi() {
        val address = "AA:BB:CC:DD:EE:FF"
        val device = getBluetoothDevice(address)
        val connection = BluetoothConnectionTracker.DeviceConnection(
            device = device,
            rssi = -50,
            isClient = true
        )

        tracker.addDeviceConnection(address, connection)
        tracker.updateScanRSSI(address, -80)

        assertEquals(-50, tracker.getBestRSSI(address))
    }

    @Test
    fun bestRssiFallsBackToScanWhenConnectionMissing() {
        val address = "11:22:33:44:55:66"

        tracker.updateScanRSSI(address, -72)

        assertEquals(-72, tracker.getBestRSSI(address))
    }

    @Test
    fun bestRssiFallsBackWhenConnectionRssiUnset() {
        val address = "22:33:44:55:66:77"
        val device = getBluetoothDevice(address)
        val connection = BluetoothConnectionTracker.DeviceConnection(
            device = device,
            rssi = Int.MIN_VALUE,
            isClient = false
        )

        tracker.addDeviceConnection(address, connection)
        tracker.updateScanRSSI(address, -64)

        assertEquals(-64, tracker.getBestRSSI(address))
    }

    @Test
    fun subscribedDevicesAreTracked() {
        val device = getBluetoothDevice("33:44:55:66:77:88")

        tracker.addSubscribedDevice(device)

        assertEquals(1, tracker.getSubscribedDevices().size)

        tracker.removeSubscribedDevice(device)

        assertTrue(tracker.getSubscribedDevices().isEmpty())
    }

    @Test
    fun removePendingConnectionClearsEntry() {
        val address = "77:88:99:AA:BB:CC"

        tracker.addPendingConnection(address)
        tracker.removePendingConnection(address)

        assertTrue(tracker.isConnectionAttemptAllowed(address))
        assertNull(tracker.getDeviceConnection(address))
    }

    private fun getBluetoothDevice(address: String): BluetoothDevice {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        assumeNotNull(adapter)
        return adapter!!.getRemoteDevice(address)
    }
}
