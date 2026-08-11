package com.meerkly.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meerkly.android.R
import com.meerkly.android.ui.components.EmptyState
import com.meerkly.android.ui.nav.NavState
import com.meerkly.android.ui.nav.WindowWidth

/**
 * Placeholder until the account API can describe a device.
 *
 * `GET /api/credits` currently returns only `machine_id` and `credits`, so a
 * real list here would be rows of hex ids with no name, platform or online
 * state — worse than saying nothing. The server already holds all of it (the
 * `devices` table plus Redis presence, which the web portal renders), so this
 * becomes the full screen once that response is extended.
 */
@Composable
fun DevicesScreen(viewModel: MainViewModel, nav: NavState, width: WindowWidth) {
    EmptyState(
        title = stringResource(R.string.devices_soon_title),
        note = stringResource(R.string.devices_soon_note),
    )
}
