package com.thoughtcrimes.securesms.conversation.v2.menus

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.AsyncTask
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.beldex.libbchat.messaging.messages.control.ExpirationTimerUpdate
import com.beldex.libbchat.messaging.sending_receiving.MessageSender
import com.beldex.libbchat.messaging.sending_receiving.leave
import com.beldex.libbchat.utilities.Contact
import com.beldex.libbchat.utilities.ExpirationUtil
import com.beldex.libbchat.utilities.GroupUtil.doubleDecodeGroupID
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.beldex.libbchat.utilities.recipients.Recipient
import com.beldex.libsignal.utilities.Log
import com.beldex.libsignal.utilities.guava.Optional
import com.beldex.libsignal.utilities.toHexString
import com.thoughtcrimes.securesms.*
import com.thoughtcrimes.securesms.calls.WebRtcCallActivity
import com.thoughtcrimes.securesms.contacts.ContactSelectionListItem
import com.thoughtcrimes.securesms.contacts.SelectContactsActivity
import com.thoughtcrimes.securesms.conversation.v2.ConversationActivityV2
import com.thoughtcrimes.securesms.conversation.v2.utilities.NotificationUtils
import com.thoughtcrimes.securesms.dependencies.DatabaseComponent
import com.thoughtcrimes.securesms.groups.EditClosedGroupActivity
import com.thoughtcrimes.securesms.groups.EditClosedGroupActivity.Companion.groupIDKey
import com.thoughtcrimes.securesms.preferences.PrivacySettingsActivity
import com.thoughtcrimes.securesms.service.WebRtcCallService
import com.thoughtcrimes.securesms.util.BitmapUtil
import com.thoughtcrimes.securesms.util.getColorWithID
import java.io.IOException
import android.content.*
import android.content.pm.PackageManager
import android.view.*
import io.beldex.bchat.R
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.content.ContextCompat.getSystemService




object ConversationMenuHelper {

    fun onPrepareOptionsMenu(menu: Menu, inflater: MenuInflater, thread: Recipient, threadId: Long, context: Context, onOptionsItemSelected: (MenuItem) -> Unit) {
        // Prepare
        menu.clear()
        val isOpenGroup = thread.isOpenGroupRecipient
        // Base menu (options that should always be present)
        inflater.inflate(R.menu.menu_conversation, menu)
        // Expiring messages
        //New Line v32
        if (!isOpenGroup && thread.hasApprovedMe()) {
            if (thread.expireMessages > 0) {
                inflater.inflate(R.menu.menu_conversation_expiration_on, menu)
                val item = menu.findItem(R.id.menu_expiring_messages)
                val actionView = item.actionView
                val iconView = actionView.findViewById<ImageView>(R.id.menu_badge_icon)
                val badgeView = actionView.findViewById<TextView>(R.id.expiration_badge)
                @ColorInt val color = context.resources.getColorWithID(R.color.text, context.theme)
                iconView.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                badgeView.text = ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, thread.expireMessages)
                actionView.setOnClickListener { onOptionsItemSelected(item) }
            } else {
                inflater.inflate(R.menu.menu_conversation_expiration_off, menu)
            }
        }
        // One-on-one chat menu (options that should only be present for one-on-one chats)
        if (thread.isContactRecipient) {
            if (thread.isBlocked) {
                inflater.inflate(R.menu.menu_conversation_unblock, menu)
            } else {
                inflater.inflate(R.menu.menu_conversation_block, menu)
            }
        }
        // Secret group menu (options that should only be present in secret groups)
        if (thread.isClosedGroupRecipient) {
            inflater.inflate(R.menu.menu_conversation_closed_group, menu)
        }
        // Social group menu
        if (isOpenGroup) {
            inflater.inflate(R.menu.menu_conversation_open_group, menu)
        }
        // Muting
        if (thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_muted, menu)
        } else {
            inflater.inflate(R.menu.menu_conversation_unmuted, menu)
        }

        if (thread.isGroupRecipient && !thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_notification_settings, menu)
        }

        //SteveJosephh21
        if (!thread.isGroupRecipient && thread.hasApprovedMe()) {
            inflater.inflate(R.menu.menu_conversation_call, menu)
        }

        // Search
        val searchViewItem = menu.findItem(R.id.menu_search)
        (context as ConversationActivityV2).searchViewItem = searchViewItem
        val searchView = searchViewItem.actionView as SearchView

        val queryListener = object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                context.onSearchQueryUpdated(query)
                Log.d("Beldex","Search Query text change")
                return true
            }
        }
        searchViewItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Log.d("Beldex","Search expand listener")
                searchView.setOnQueryTextListener(queryListener)
                context.onSearchOpened()
                for (i in 0 until menu.size()) {
                    if (menu.getItem(i) != searchViewItem) {
                        menu.getItem(i).isVisible = false
                    }
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(null)
                context.onSearchClosed()
                return true
            }
        })

    }

    fun onOptionItemSelected(context: Context, item: MenuItem, thread: Recipient): Boolean {
        when (item.itemId) {
            R.id.menu_view_all_media -> { showAllMedia(context, thread) }
            R.id.menu_search -> { search(context) }
            R.id.menu_add_shortcut -> { addShortcut(context, thread) }
            R.id.menu_expiring_messages -> { showExpiringMessagesDialog(context, thread) }
            R.id.menu_expiring_messages_off -> { showExpiringMessagesDialog(context, thread) }
            R.id.menu_unblock -> { unblock(context, thread) }
            R.id.menu_block -> { block(context, thread) }
            R.id.menu_copy_bchat_id -> { copyBchatID(context, thread) }
            R.id.menu_edit_group -> { editClosedGroup(context, thread) }
            R.id.menu_leave_group -> { leaveClosedGroup(context, thread) }
            R.id.menu_invite_to_open_group -> { inviteContacts(context, thread) }
            R.id.menu_unmute_notifications -> { unmute(context, thread) }
            R.id.menu_mute_notifications -> { mute(context, thread) }
            R.id.menu_notification_settings -> { setNotifyType(context, thread) }
          /*  R.id.menu_call -> {
                *//*Hales63*//*
                if (isOnline(context)) {
                    Log.d("Beldex","Call state issue called")
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_PHONE_STATE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d("Beldex","Call state issue called 1")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Log.d("Beldex","Call state issue called 2")
                            tm.registerTelephonyCallback(
                                context.mainExecutor,
                                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                                    override fun onCallStateChanged(state: Int) {
                                        when (state) {
                                            TelephonyManager.CALL_STATE_RINGING -> {
                                                Log.d("Beldex","Call state issue called 3")
                                                Toast.makeText(
                                                    context,
                                                    "BChat call won't allow ,Because your phone call ringing",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            TelephonyManager.CALL_STATE_OFFHOOK -> {
                                                Log.d("Beldex","Call state issue called 4")
                                                Toast.makeText(
                                                    context,
                                                    "BChat call won't allow ,Because your phone call is on going",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                            }
                                            TelephonyManager.CALL_STATE_IDLE -> {
                                                Log.d("Beldex","Call state issue called 5")
                                                call(context, thread)
                                            }
                                        }
                                    }
                                })

                        } else {
                            Log.d("Beldex","Call state issue called 6")
                            tm.listen(object : PhoneStateListener() {
                                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                                    super.onCallStateChanged(state, phoneNumber)
                                    when (state) {
                                        TelephonyManager.CALL_STATE_RINGING -> {
                                            Log.d("Beldex","Call state issue called 7")
                                            Toast.makeText(
                                                context,
                                                "BChat call won't allow ,Because your phone call ringing",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                                            Log.d("Beldex","Call state issue called 8")
                                            Toast.makeText(
                                                context,
                                                "BChat call won't allow ,Because your phone call is on going",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        }
                                        TelephonyManager.CALL_STATE_IDLE -> {
                                            Log.d("Beldex","Call state issue called 9" )
                                            call(context, thread)
                                        }
                                    }
                                }
                            }, PhoneStateListener.LISTEN_CALL_STATE)
                        }
                    }
                    else{

                        Log.d("Beldex","Call state issue called else")
                    }

                } else {
                    Toast.makeText(context, "Check your Internet", Toast.LENGTH_SHORT).show()
                }
            }*/
        }
        return true
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                return true
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                return true
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                return true
            }
        }
        return false
    }




    fun showAllMedia(context: Context, thread: Recipient) {
        val intent = Intent(context, MediaOverviewActivity::class.java)
        intent.putExtra(MediaOverviewActivity.ADDRESS_EXTRA, thread.address)
        val activity = context as AppCompatActivity
        activity.startActivity(intent)
    }

    private fun search(context: Context) {
        val searchViewModel = (context as ConversationActivityV2).searchViewModel
        searchViewModel.onSearchOpened()
    }

    //New Line
    private fun call(context: Context, thread: Recipient) {

        if (!TextSecurePreferences.isCallNotificationsEnabled(context)) {
           /* AlertDialog.Builder(context)
                .setTitle(R.string.ConversationActivity_call_title)
                .setMessage(R.string.ConversationActivity_call_prompt)
                .setPositiveButton(R.string.activity_settings_title) { _, _ ->
                    val intent = Intent(context, PrivacySettingsActivity::class.java)
                    context.startActivity(intent)
                }
                .setNeutralButton(R.string.cancel) { d, _ ->
                    d.dismiss()
                }.show()*/
            //SteveJosephh22
            val factory = LayoutInflater.from(context)
            val callPermissionDialogView: View = factory.inflate(R.layout.call_permissions_dialog_box, null)
            val callPermissionDialog = AlertDialog.Builder(context).create()
            callPermissionDialog.setView(callPermissionDialogView)
            callPermissionDialogView.findViewById<TextView>(R.id.settingsDialogBoxButton).setOnClickListener{
                val intent = Intent(context, PrivacySettingsActivity::class.java)
                context.startActivity(intent)
                callPermissionDialog.dismiss()
            }
            callPermissionDialogView.findViewById<TextView>(R.id.cancelDialogBoxButton).setOnClickListener{
                    callPermissionDialog.dismiss()
            }
            callPermissionDialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            callPermissionDialog.show()
            return
        }

        val service = WebRtcCallService.createCall(context, thread)
        context.startService(service)

        val activity = Intent(context, WebRtcCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(activity)

    }

    @SuppressLint("StaticFieldLeak")
    private fun addShortcut(context: Context, thread: Recipient) {
        object : AsyncTask<Void?, Void?, IconCompat?>() {

            override fun doInBackground(vararg params: Void?): IconCompat? {
                var icon: IconCompat? = null
                val contactPhoto = thread.contactPhoto
                if (contactPhoto != null) {
                    try {
                        var bitmap = BitmapFactory.decodeStream(contactPhoto.openInputStream(context))
                        bitmap = BitmapUtil.createScaledBitmap(bitmap, 300, 300)
                        icon = IconCompat.createWithAdaptiveBitmap(bitmap)
                    } catch (e: IOException) {
                        // Do nothing
                    }
                }
                if (icon == null) {
                    icon = IconCompat.createWithResource(context, if (thread.isGroupRecipient) R.drawable.ic_shortcut_group else R.drawable.ic_shortcut_person)
                }
                return icon
            }

            override fun onPostExecute(icon: IconCompat?) {
                val name = Optional.fromNullable<String>(thread.name)
                    .or(Optional.fromNullable<String>(thread.profileName))
                    .or(thread.toShortString())
                val shortcutInfo = ShortcutInfoCompat.Builder(context, thread.address.serialize() + '-' + System.currentTimeMillis())
                    .setIcon(icon)
                    .setShortLabel(name)
                    .setIntent(ShortcutLauncherActivity.createIntent(context, thread.address))
                    .build()
                if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
                    Toast.makeText(context, context.resources.getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun showExpiringMessagesDialog(context: Context, thread: Recipient) {
        if (thread.isClosedGroupRecipient) {
            val group = DatabaseComponent.get(context).groupDatabase().getGroup(thread.address.toGroupString()).orNull()
            if (group?.isActive == false) { return }
        }
        ExpirationDialog.show(context, thread.expireMessages) { expirationTime: Int ->
            DatabaseComponent.get(context).recipientDatabase().setExpireMessages(thread, expirationTime)
            val message = ExpirationTimerUpdate(expirationTime)
            message.recipient = thread.address.serialize()
            message.sentTimestamp = System.currentTimeMillis()
            val expiringMessageManager = ApplicationContext.getInstance(context).expiringMessageManager
            expiringMessageManager.setExpirationTimer(message)
            MessageSender.send(message, thread.address)
            val activity = context as AppCompatActivity
            activity.invalidateOptionsMenu()
        }
    }

    private fun unblock(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val title = R.string.ConversationActivity_unblock_this_contact_question
        val message = R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact
        val dialog = AlertDialog.Builder(context, R.style.BChatAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ConversationActivity_unblock) { _, _ ->
                DatabaseComponent.get(context).recipientDatabase()
                    .setBlocked(thread, false)
            }.show()

        //New Line
        val textView: TextView? = dialog.findViewById(android.R.id.message)
        val face: Typeface = Typeface.createFromAsset(context.assets,"fonts/poppins_medium.ttf")
        textView!!.typeface = face
    }

    private fun block(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val title = R.string.RecipientPreferenceActivity_block_this_contact_question
        val message = R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact
        val dialog = AlertDialog.Builder(context,R.style.BChatAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block) { _, _ ->
                DatabaseComponent.get(context).recipientDatabase()
                    .setBlocked(thread, true)
            }.show()

        //New Line
        val textView: TextView? = dialog.findViewById(android.R.id.message)
        val face: Typeface = Typeface.createFromAsset(context.assets,"fonts/poppins_medium.ttf")
        textView!!.typeface = face
    }

    private fun copyBchatID(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val bchatID = thread.address.toString()
        val clip = ClipData.newPlainText("BChat ID", bchatID)
        val activity = context as AppCompatActivity
        val manager = activity.getSystemService(PassphraseRequiredActionBarActivity.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun editClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }
        val intent = Intent(context, EditClosedGroupActivity::class.java)
        val groupID: String = thread.address.toGroupString()
        intent.putExtra(groupIDKey, groupID)
        context.startActivity(intent)
    }

    private fun leaveClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }
        val group = DatabaseComponent.get(context).groupDatabase().getGroup(thread.address.toGroupString()).orNull()
        val admins = group.admins
        val bchatID = TextSecurePreferences.getLocalNumber(context)
        val isCurrentUserAdmin = admins.any { it.toString() == bchatID }
        val message = if (isCurrentUserAdmin) {
            "Because you are the creator of this group it will be deleted for everyone. This cannot be undone."
        } else {
            context.resources.getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group)
        }
        val builder = AlertDialog.Builder(context,R.style.BChatAlertDialog_remove_new)
        .setTitle(context.resources.getString(R.string.ConversationActivity_leave_group))
        .setCancelable(true)
        .setMessage(message)
        .setPositiveButton(R.string.leave) { _, _ ->
            var groupPublicKey: String?
            var isClosedGroup: Boolean
            try {
                groupPublicKey = doubleDecodeGroupID(thread.address.toString()).toHexString()
                isClosedGroup = DatabaseComponent.get(context).beldexAPIDatabase().isClosedGroup(groupPublicKey)
            } catch (e: IOException) {
                groupPublicKey = null
                isClosedGroup = false
            }
            try {
                if (isClosedGroup) {
                    MessageSender.leave(groupPublicKey!!, true)
                } else {
                    Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton(R.string.no, null)
        .show()

        //New Line
        val textView: TextView? = builder.findViewById(android.R.id.message)
        val face: Typeface = Typeface.createFromAsset(context.assets,"fonts/poppins_medium.ttf")
        textView!!.typeface = face
    }

    private fun inviteContacts(context: Context, thread: Recipient) {
        if (!thread.isOpenGroupRecipient) { return }
        val intent = Intent(context, SelectContactsActivity::class.java)
        val activity = context as AppCompatActivity
        activity.startActivityForResult(intent, ConversationActivityV2.INVITE_CONTACTS)
    }

    private fun unmute(context: Context, thread: Recipient) {
        DatabaseComponent.get(context).recipientDatabase().setMuted(thread, 0)
    }

    private fun mute(context: Context, thread: Recipient) {
        MuteDialog.show(context) { until: Long ->
            DatabaseComponent.get(context).recipientDatabase().setMuted(thread, until)
        }
    }

    private fun setNotifyType(context: Context, thread: Recipient) {
        NotificationUtils.showNotifyDialog(context, thread) { notifyType ->
            DatabaseComponent.get(context).recipientDatabase().setNotifyType(thread, notifyType)
        }
    }

}


