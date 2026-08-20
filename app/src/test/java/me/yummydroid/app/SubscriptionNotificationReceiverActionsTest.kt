package me.yummydroid.app

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionNotificationReceiverActionsTest {
    @Test
    fun alarmReceiverAcceptsOnlyItsPrivateAction() {
        assertTrue(isSubscriptionNotificationAlarmAction(SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS))
        assertFalse(isSubscriptionNotificationAlarmAction(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(isSubscriptionNotificationAlarmAction(null))
    }

    @Test
    fun dismissReceiverAcceptsOnlyItsPrivateAction() {
        assertTrue(
            isSubscriptionNotificationDismissAction(
                SubscriptionNotificationDismissReceiver.ACTION_DISMISS_PROFILE_NOTIFICATIONS,
            ),
        )
        assertFalse(isSubscriptionNotificationDismissAction(Intent.ACTION_SCREEN_ON))
        assertFalse(isSubscriptionNotificationDismissAction(null))
    }

    @Test
    fun rescheduleReceiverAcceptsEveryDeclaredSystemAction() {
        assertTrue(isSubscriptionNotificationRescheduleAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isSubscriptionNotificationRescheduleAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(isSubscriptionNotificationRescheduleAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(isSubscriptionNotificationRescheduleAction(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun rescheduleReceiverRejectsUnrelatedAndMissingActions() {
        assertFalse(isSubscriptionNotificationRescheduleAction(Intent.ACTION_SCREEN_ON))
        assertFalse(isSubscriptionNotificationRescheduleAction(null))
    }
}
