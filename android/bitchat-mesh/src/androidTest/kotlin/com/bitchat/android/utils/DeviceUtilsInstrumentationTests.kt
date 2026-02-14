package com.bitchat.android.utils

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUtilsInstrumentationTests {
    @Test
    fun isTabletReturnsFalseForPhoneSizedDisplay() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeFalse(DeviceUtils.isTablet(base))
        assertFalse(DeviceUtils.isTablet(base))
    }

    @Test
    fun isTabletReturnsTrueForLargeSmallestWidth() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration(base.resources.configuration).apply {
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
            smallestScreenWidthDp = 700
        }
        val context = base.createConfigurationContext(config)

        assertTrue(DeviceUtils.isTablet(context))
    }

    @Test
    fun isTabletReturnsTrueForLargeScreenLayout() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration(base.resources.configuration).apply {
            screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE
            smallestScreenWidthDp = 480
        }
        val context = base.createConfigurationContext(config)

        assertTrue(DeviceUtils.isTablet(context))
    }
}
