package com.bitchat.android.mesh

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothPermissionManagerInstrumentationTests {
    private class TestPermissionContext(
        base: Context,
        private val permissions: Map<String, Int>
    ) : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            return permissions[permission] ?: PackageManager.PERMISSION_DENIED
        }

        override fun checkSelfPermission(permission: String): Int {
            return permissions[permission] ?: PackageManager.PERMISSION_DENIED
        }
    }

    @Test
    fun hasBluetoothPermissions_returnsTrueWhenAllGranted() {
        val required = requiredPermissions()
        val permissionState = required.associateWith { PackageManager.PERMISSION_GRANTED }
        val context = TestPermissionContext(
            ApplicationProvider.getApplicationContext(),
            permissionState
        )

        val manager = BluetoothPermissionManager(context)

        assertTrue(manager.hasBluetoothPermissions())
    }

    @Test
    fun hasBluetoothPermissions_returnsFalseWhenMissingPermission() {
        val required = requiredPermissions().toMutableList()
        val permissionState = required.associateWith { PackageManager.PERMISSION_GRANTED }.toMutableMap()
        permissionState[required.first()] = PackageManager.PERMISSION_DENIED

        val context = TestPermissionContext(
            ApplicationProvider.getApplicationContext(),
            permissionState
        )

        val manager = BluetoothPermissionManager(context)

        assertFalse(manager.hasBluetoothPermissions())
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            )
        }

        permissions.addAll(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        return permissions
    }
}
