package com.meerkly.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineIdManagerTest {

    @Test
    fun generatesOnceAndIsStableAcrossReads() {
        val context = RuntimeEnvironment.getApplication()
        val first = MachineIdManager.getMachineId(context)
        val second = MachineIdManager.getMachineId(context)

        assertTrue("machine id should not be blank", first.isNotBlank())
        assertEquals("machine id should be stable", first, second)
    }
}
