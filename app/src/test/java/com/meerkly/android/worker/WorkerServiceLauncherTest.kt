package com.meerkly.android.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single eligibility rule every start path (Activity, sign-in, boot) shares. */
class WorkerServiceLauncherTest {

    @Test
    fun `eligible only when enabled and a publisher id is known`() {
        assertTrue(WorkerServiceLauncher.eligible(workerEnabled = true, publisherId = "pub_abc"))
    }

    @Test
    fun `a stopped worker is never eligible`() {
        // The sticky-Stop invariant: nothing auto-starts a worker the user
        // turned off — not boot, not app open, not a sticky restart.
        assertFalse(WorkerServiceLauncher.eligible(workerEnabled = false, publisherId = "pub_abc"))
    }

    @Test
    fun `no publisher id means nothing to earn for`() {
        assertFalse(WorkerServiceLauncher.eligible(workerEnabled = true, publisherId = null))
        assertFalse(WorkerServiceLauncher.eligible(workerEnabled = true, publisherId = ""))
        assertFalse(WorkerServiceLauncher.eligible(workerEnabled = true, publisherId = "   "))
    }

    @Test
    fun `a notification grant reposts only when the worker should be running`() {
        assertTrue(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = true, workerEnabled = true, publisherId = "pub_abc",
            ),
        )
        assertFalse(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = false, workerEnabled = true, publisherId = "pub_abc",
            ),
        )
        assertFalse(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = true, workerEnabled = false, publisherId = "pub_abc",
            ),
        )
    }
}
