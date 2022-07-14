package com.thoughtcrimes.securesms.preferences;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.thoughtcrimes.securesms.permissions.Permissions;
import com.thoughtcrimes.securesms.util.Trimmer;

import com.beldex.libbchat.utilities.TextSecurePreferences;
import com.beldex.libsignal.utilities.Log;

import io.beldex.bchat.R;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String TAG = ChatsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
        .setOnPreferenceClickListener(new TrimNowClickListener());
    findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
        .setOnPreferenceChangeListener(new TrimLengthValidationListener());

  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(getActivity());
      /*AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now);
      builder.setMessage(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_this_will_immediately_trim_all_conversations_to_the_d_most_recent_messages,
                                                          threadLengthLimit, threadLengthLimit));
      builder.setPositiveButton(R.string.ApplicationPreferencesActivity_delete,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Trimmer.trimAllThreads(getActivity(), threadLengthLimit);
          }
        });

      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();*/
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),R.style.BChatAlertDialog);
      View dialogView = View.inflate(getActivity(),R.layout.delete_all_old_messages_dialog, null);

      builder.setView(dialogView);
      TextView message = dialogView.findViewById(R.id.delete_all_old_messages_dialog_message_textView);
      TextView cancel  = dialogView.findViewById(R.id.delete_all_old_messages_dialog_cancel);
      TextView delete  = dialogView.findViewById(R.id.delete_all_old_messages_dialog_delete);
      message.setText(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_this_will_immediately_trim_all_conversations_to_the_d_most_recent_messages,
              threadLengthLimit, threadLengthLimit));
      AlertDialog alertDialog = builder.create();
      alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      alertDialog.show();
      cancel.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            alertDialog.cancel();
        }
      });
      delete.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Trimmer.trimAllThreads(getActivity(), threadLengthLimit);
          alertDialog.cancel();
        }
      });
      return true;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    public TrimLengthValidationListener() {
      EditTextPreference preference = findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH);
      onPreferenceChange(preference, preference.getText());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      if (newValue == null || ((String)newValue).trim().length() == 0) {
        return false;
      }

      int value;
      try {
        value = Integer.parseInt((String)newValue);
      } catch (NumberFormatException nfe) {
        Log.w(TAG, nfe);
        return false;
      }

      if (value < 1) {
        return false;
      }

      preference.setSummary(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_messages_per_conversation, value, value));
      return true;
    }
  }

}
