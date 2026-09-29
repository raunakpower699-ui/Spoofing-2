package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Raunak Exploits", appName)
    }

    @Test
    fun `hardware info returns valid processor and cores`() {
        val info = HardwareInfoHelper.getDeviceInfo()
        assertNotNull(info.processorName)
        assertNotNull(info.architecture)
        assertEquals(Runtime.getRuntime().availableProcessors(), info.totalCores)
    }

    @Test
    fun `boost mode enum values valid`() {
        assertEquals(3, BoostMode.values().size)
        assertEquals("BOOST", BoostMode.BOOST.name)
    }
}
