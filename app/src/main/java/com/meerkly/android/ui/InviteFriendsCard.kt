package com.meerkly.android.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meerkly.android.R
import com.meerkly.android.model.Earnings
import com.meerkly.android.ui.components.BrandCard
import com.meerkly.android.ui.components.HeartIcon
import com.meerkly.android.ui.components.IconChip
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import com.meerkly.android.ui.theme.PinkDeep
import com.meerkly.android.util.Formatters

/** Only when the server has referrals on for this account and we know the link to share. */
internal fun showInviteCard(earnings: Earnings?, referralUrl: String?): Boolean =
    earnings?.referralsEnabled == true && !referralUrl.isNullOrBlank()

/** Plain-text ACTION_SEND for the invite link; wrap in Intent.createChooser to launch. */
internal fun inviteShareIntent(message: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }

/**
 * "Invite friends": the referral bonus (lifetime, payable, held), how many
 * friends and friends-of-friends joined, the rates as the server states them,
 * and a Share button. Plain state + a lambda so DesignRenderTest can draw it.
 */
@Composable
internal fun InviteFriendsCard(earnings: Earnings, onShare: () -> Unit) {
    BrandCard {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconChip(listOf(Pink, PinkDeep)) { HeartIcon() }
            Text(
                text = stringResource(R.string.invite_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = InkSoft,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = Formatters.usd(earnings.referralLifetimeUsd),
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Ink,
            )
            Text(
                text = stringResource(R.string.invite_payable_note, Formatters.usd(earnings.referralUsd)),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            if (earnings.referralHeldUsd > 0.0) {
                Text(
                    text = stringResource(R.string.invite_held_note, Formatters.usd(earnings.referralHeldUsd)),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
            Text(
                text = stringResource(
                    R.string.invite_counts,
                    earnings.referralCounts.level1,
                    earnings.referralCounts.level2,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            val l1 = earnings.referralRates.level1
            val l2 = earnings.referralRates.level2
            Text(
                text = if (l1 != null && l2 != null) {
                    stringResource(R.string.invite_explainer, Formatters.usd(l1), Formatters.usd(l2))
                } else {
                    stringResource(R.string.invite_explainer_generic)
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Cream),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.invite_share), fontWeight = FontWeight.Bold)
            }
        }
    }
}
