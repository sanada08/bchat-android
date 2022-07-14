package com.thoughtcrimes.securesms.notifications;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import com.thoughtcrimes.securesms.home.HomeActivity;
import com.beldex.libbchat.utilities.recipients.Recipient;
import com.thoughtcrimes.securesms.home.HomeActivity;
import com.beldex.libbchat.utilities.NotificationPrivacyPreference;
import com.beldex.libbchat.utilities.TextSecurePreferences;

import io.beldex.bchat.R;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    Intent intent = new Intent(context, HomeActivity.class);

    setSmallIcon(R.drawable.ic_notification);
    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    setContentTitle(context.getString(R.string.MessageNotifier_pending_signal_messages));
    setContentText(context.getString(R.string.MessageNotifier_you_have_pending_signal_messages));
    setTicker(context.getString(R.string.MessageNotifier_you_have_pending_signal_messages));

    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, Recipient.VibrateState.DEFAULT);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }
}
