package com.meerkly.android.data

import android.content.Context
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

    @Test
    fun `a fresh id carries the network's dev prefix`() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_prefs_prefix", Context.MODE_PRIVATE)

        val id = MachineIdManager.getMachineId(prefs)

        assertTrue("expected a dev_ prefix, got $id", id.startsWith("dev_"))
        assertEquals(4 + 36, id.length)
    }

    @Test
    fun `an id stored before the prefix existed is migrated in place`() {
        // 1.x installs hold a bare UUID. Minting a new id on upgrade would
        // orphan the device row the account already has a label on.
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_prefs_legacy", Context.MODE_PRIVATE)
        val legacy = "11111111-2222-3333-4444-555555555555"
        prefs.edit().putString("machine_id", legacy).commit()

        val id = MachineIdManager.getMachineId(prefs)

        assertEquals("dev_$legacy", id)
        assertEquals("dev_$legacy", prefs.getString("machine_id", null))
    }

    @Test
    fun `a migrated id is stable across reads`() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_prefs_stable", Context.MODE_PRIVATE)
        prefs.edit().putString("machine_id", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").commit()

        val first = MachineIdManager.getMachineId(prefs)
        val second = MachineIdManager.getMachineId(prefs)

        assertEquals(first, second)
    }
}
