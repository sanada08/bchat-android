package com.thoughtcrimes.securesms.preferences

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.beldex.libbchat.avatars.AvatarHelper
import io.beldex.bchat.R
import io.beldex.bchat.databinding.ActivitySettingsBinding
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.successUi
import com.beldex.libbchat.utilities.Address
import com.beldex.libbchat.utilities.ProfileKeyUtil
import com.beldex.libbchat.utilities.ProfilePictureUtilities
import com.beldex.libbchat.utilities.SSKEnvironment.ProfileManagerProtocol
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.thoughtcrimes.securesms.PassphraseRequiredActionBarActivity
import com.thoughtcrimes.securesms.applock.AppLockDetailsActivity
import com.thoughtcrimes.securesms.avatar.AvatarSelection
import com.thoughtcrimes.securesms.changelog.ChangeLogActivity
import com.thoughtcrimes.securesms.contacts.BlockedContactActivity
import com.thoughtcrimes.securesms.crypto.IdentityKeyUtil
import com.thoughtcrimes.securesms.home.PathActivity
import com.thoughtcrimes.securesms.messagerequests.MessageRequestsActivity
import com.thoughtcrimes.securesms.mms.GlideApp
import com.thoughtcrimes.securesms.util.*
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Date
import com.thoughtcrimes.securesms.mms.GlideRequests
import com.thoughtcrimes.securesms.permissions.Permissions
import com.thoughtcrimes.securesms.profiles.ProfileMediaConstraints

class SettingsActivity : PassphraseRequiredActionBarActivity(), Animation.AnimationListener {
    private lateinit var binding: ActivitySettingsBinding
    private var displayNameEditActionMode: ActionMode? = null
        set(value) {
            field = value; handleDisplayNameEditActionModeChanged()
        }
    private lateinit var glide: GlideRequests
    private var displayNameToBeUploaded: String? = null
    private var profilePictureToBeUploaded: ByteArray? = null
    private var tempFile: File? = null

    private val hexEncodedPublicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(this)!!
        }

    companion object {
        const val updatedProfileResultCode = 1234
    }

    //New Line
    private lateinit var animation1: Animation
    private lateinit var animation2: Animation
    private var isFrontOfCardShowing = true

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpActionBarBchatLogo("My Account")
        val displayName = TextSecurePreferences.getProfileName(this) ?: hexEncodedPublicKey
        glide = GlideApp.with(this)

        val size = toPx(280, resources)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        binding.qrCodeImageView.setImageBitmap(qrCode)
        binding.qrCodeShareButton.setOnClickListener { shareQRCode() }

        // apply animation from to_middle
        animation1 = AnimationUtils.loadAnimation(this, R.anim.to_middle)
        animation1.setAnimationListener(this)

        // apply animation from to_middle
        animation2 = AnimationUtils.loadAnimation(this, R.anim.from_middle)
        animation2.setAnimationListener(this)

        with(binding) {
            profilePictureView.glide = glide
            profilePictureView.publicKey = hexEncodedPublicKey
            profilePictureView.displayName = displayName
            profilePictureView.isLarge = true
            profilePictureView.update()
            //New Line
            profilePictureView.setOnClickListener { showEditProfilePictureUI() }

            profilePictureViewButton.setOnClickListener { showEditProfilePictureUI() }
            ctnGroupNameSection.setOnClickListener {
                startActionMode(
                    DisplayNameEditActionModeCallback()
                )
            }
            btnGroupNameDisplay.text = displayName
            publicKeyTextView.text = hexEncodedPublicKey
            publicKeyCardView.setOnClickListener { copyPublicKey() }
            shareButton.setOnClickListener { sharePublicKey() }
            pathButton.setOnClickListener { showPath() }
            pathContainer.disableClipping()
            privacyButton.setOnClickListener { showPrivacySettings() }
            notificationsButton.setOnClickListener { showNotificationSettings() }
            chatsButton.setOnClickListener { showChatSettings() }
            sendInvitationButton.setOnClickListener { sendInvitation() }
            faqButton.setOnClickListener { showFAQ() }
            surveyButton.setOnClickListener { showSurvey() }
            seedButton.setOnClickListener { showSeed() }
            clearAllDataButton.setOnClickListener { clearAllData() }
            debugLogButton.setOnClickListener { shareLogs() }
            blockedcontactbutton.setOnClickListener{ blockedContacts()}

            //New Line
            changeLogButton.setOnClickListener { showChangeLog() }
            //New Line
            appLockButton.setOnClickListener { showAppLockDetailsPage() }
            val isLightMode = UiModeUtilities.isDayUiMode(this@SettingsActivity)
            //versionTextView.text = String.format(getString(R.string.version_s), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            beldexAddressTextView.text = IdentityKeyUtil.retrieve(
                this@SettingsActivity,
                IdentityKeyUtil.IDENTITY_W_ADDRESS_PREF
            )
            profileButton.setOnClickListener {
                it.isEnabled = false

                // stop animation
                profileCardView.clearAnimation()
                profileCardView.animation = animation1

                // start the animation
                profileCardView.startAnimation(animation1)
            }
            qrCodeButton.setOnClickListener {
                it.isEnabled = false

                // stop animation
                profileCardView.clearAnimation()
                profileCardView.animation = animation1

                // start the animation
                profileCardView.startAnimation(animation1)
            }

            beldexAddressCardView.setOnClickListener { copyBeldexAddress() }
            beldexAddressShareButton.setOnClickListener { shareBeldexAddress() }
        }
    }

    override fun onAnimationEnd(animation: Animation) {
        if (animation === animation1) {
            // check whether the front of the card is showing
            if (isFrontOfCardShowing) {
                // set image from card_front to card_back
                binding.backViewLinearLayout.visibility = View.VISIBLE
                binding.frontViewLinearLayout.visibility = View.GONE
            } else {
                // set image from card_back to card_front
                binding.backViewLinearLayout.visibility = View.GONE
                binding.frontViewLinearLayout.visibility = View.VISIBLE
            }
            // stop the animation of the ImageView
            binding.profileCardView.clearAnimation()
            binding.profileCardView.animation = animation2
            // allow fine-grained control
            // over the start time and invalidation
            binding.profileCardView.startAnimation(animation2)
        } else {
            isFrontOfCardShowing = !isFrontOfCardShowing
            binding.profileButton.isEnabled = true
            binding.qrCodeButton.isEnabled = true
        }
    }

    override fun onAnimationRepeat(animation: Animation?) {
        // TODO Auto-generated method stub
    }

    override fun onAnimationStart(animation: Animation?) {
        // TODO Auto-generated method stub
    }

    private fun shareQRCode() {
        val directory = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val fileName = "$hexEncodedPublicKey.png"
        val file = File(directory, fileName)
        file.createNewFile()
        val fos = FileOutputStream(file)
        val size = toPx(280, resources)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, FileProviderUtil.getUriFor(this, file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "image/png"
        startActivity(
            Intent.createChooser(
                intent,
                resources.getString(R.string.fragment_view_my_qr_code_share_title)
            )
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_general, menu)
        // Update UI mode menu icon
        /*val uiMode = UiModeUtilities.getUserSelectedUiMode(this)
        menu.findItem(R.id.action_change_theme).icon!!.level = uiMode.ordinal*/
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_qr_code -> {
                showQRCode()
                true
            }
            /*  R.id.action_change_theme -> {
                  ChangeUiModeDialog().show(supportFragmentManager, ChangeUiModeDialog.TAG)
                  true
              }*/
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            AvatarSelection.REQUEST_CODE_AVATAR -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                val outputFile = Uri.fromFile(File(cacheDir, "cropped"))
                var inputFile: Uri? = data?.data
                if (inputFile == null && tempFile != null) {
                    inputFile = Uri.fromFile(tempFile)
                }
                AvatarSelection.circularCropImage(
                    this,
                    inputFile,
                    outputFile,
                    R.string.CropImageActivity_profile_avatar
                )
            }
            AvatarSelection.REQUEST_CODE_CROP_IMAGE -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                AsyncTask.execute {
                    try {
                        profilePictureToBeUploaded = BitmapUtil.createScaledBytes(
                            this@SettingsActivity,
                            AvatarSelection.getResultUri(data),
                            ProfileMediaConstraints()
                        ).bitmap
                        Handler(Looper.getMainLooper()).post {
                            updateProfile(true)
                        }
                    } catch (e: BitmapDecodingException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    // endregion

    // region Updating
    private fun handleDisplayNameEditActionModeChanged() {
        val isEditingDisplayName = this.displayNameEditActionMode !== null

        binding.btnGroupNameDisplay.visibility =
            if (isEditingDisplayName) View.INVISIBLE else View.VISIBLE
        binding.displayNameTitleLinearLayout.visibility = if(isEditingDisplayName) View.INVISIBLE else View.VISIBLE
        binding.displayNameEditText.visibility =
            if (isEditingDisplayName) View.VISIBLE else View.INVISIBLE

        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingDisplayName) {
            binding.displayNameEditText.setText(binding.btnGroupNameDisplay.text)
            binding.displayNameEditText.selectAll()
            binding.displayNameEditText.requestFocus()
            inputMethodManager.showSoftInput(binding.displayNameEditText, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(binding.displayNameEditText.windowToken, 0)
        }
    }

    private fun updateProfile(isUpdatingProfilePicture: Boolean) {
        binding.loader.isVisible = true
        val promises = mutableListOf<Promise<*, Exception>>()
        val displayName = displayNameToBeUploaded
        if (displayName != null) {
            TextSecurePreferences.setProfileName(this, displayName)
        }
        val profilePicture = profilePictureToBeUploaded
        val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this)
        if (isUpdatingProfilePicture && profilePicture != null) {
            promises.add(ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, this))
        }
        val compoundPromise = all(promises)
        compoundPromise.successUi { // Do this on the UI thread so that it happens before the alwaysUi clause below
            if (isUpdatingProfilePicture && profilePicture != null) {
                AvatarHelper.setAvatar(
                    this,
                    Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!),
                    profilePicture
                )
                TextSecurePreferences.setProfileAvatarId(this, SecureRandom().nextInt())
                TextSecurePreferences.setLastProfilePictureUpload(this, Date().time)
                ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey)
            }
            if (profilePicture != null || displayName != null) {
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@SettingsActivity)
            }
        }
        compoundPromise.alwaysUi {
            if (displayName != null) {
                binding.btnGroupNameDisplay.text = displayName
            }
            if (isUpdatingProfilePicture && profilePicture != null) {
                binding.profilePictureView.recycle() // Clear the cached image before updating
                binding.profilePictureView.update()
            }
            displayNameToBeUploaded = null
            profilePictureToBeUploaded = null
            binding.loader.isVisible = false
        }
    }

    private fun blockedContacts() {
        val intent = Intent(this, BlockedContactActivity::class.java)
        show(intent)
    }

    // endregion

    // region Interaction

    /**
     * @return true if the update was successful.
     */
    private fun saveDisplayName(): Boolean {
        val displayName = binding.displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            Toast.makeText(
                this,
                R.string.activity_settings_display_name_missing_error,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        if (displayName.toByteArray().size > ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH) {
            Toast.makeText(
                this,
                R.string.activity_settings_display_name_too_long_error,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        displayNameToBeUploaded = displayName
        updateProfile(false)
        return true
    }

    private fun showQRCode() {
        val intent = Intent(this, ShowQRCodeWithScanQRCodeActivity::class.java)
        push(intent)
    }

    private fun showEditProfilePictureUI() {
        // Ask for an optional camera permission.
        Permissions.with(this)
            .request(Manifest.permission.CAMERA)
            .onAnyResult {
                tempFile = AvatarSelection.startAvatarSelection(this, false, true)
            }
            .execute()
    }

    private fun copyPublicKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chat ID", hexEncodedPublicKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.bchat_id_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun copyBeldexAddress() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chat ID", binding.beldexAddressTextView.text.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.beldex_address_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        val chooser = Intent.createChooser(intent, getString(R.string.share))
        startActivity(chooser)
    }

    private fun shareBeldexAddress() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, binding.beldexAddressTextView.text.toString())
        intent.type = "text/plain"
        val chooser = Intent.createChooser(intent, getString(R.string.share))
        startActivity(chooser)
    }

    private fun showPrivacySettings() {
        val intent = Intent(this, PrivacySettingsActivity::class.java)
        push(intent)
    }

    private fun showNotificationSettings() {
        val intent = Intent(this, NotificationSettingsActivity::class.java)
        push(intent)
    }

    private fun showChatSettings() {
        val intent = Intent(this, ChatSettingsActivity::class.java)
        push(intent)
    }

    private fun sendInvitation() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        val invitation =
            "Hey, I've been using BChat to chat with complete privacy and security. Come join me! Download it at https://www.beldex.io/. My BChat ID is $hexEncodedPublicKey !"
        intent.putExtra(Intent.EXTRA_TEXT, invitation)
        intent.type = "text/plain"
        val chooser =
            Intent.createChooser(intent, getString(R.string.activity_settings_invite_button_title))
        startActivity(chooser)
    }

    private fun showFAQ() {
        try {
            val url = "https://bchat.beldex.io/faq"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open URL", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPath() {
        val intent = Intent(this, PathActivity::class.java)
        show(intent)
    }

    private fun showChangeLog() {
        val intent = Intent(this, ChangeLogActivity::class.java)
        show(intent)
    }

    private fun showAppLockDetailsPage() {
        val intent = Intent(this, AppLockDetailsActivity::class.java)
        show(intent)
    }

    private fun showSurvey() {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:") // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@beldex.io"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "")
        startActivity(intent)
    }

    private fun helpTranslate() {
        try {
            val url = "https://www.beldex.io/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open URL", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSeed() {
        SeedDialog().show(supportFragmentManager, "Recovery Phrase Dialog")
    }

    private fun clearAllData() {
        ClearAllDataDialog().show(supportFragmentManager, "Clear All Data Dialog")
    }

    private fun shareLogs() {
        Permissions.with(this)
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .maxSdkVersion(Build.VERSION_CODES.P)
            .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
            .onAnyDenied {
                Toast.makeText(
                    this,
                    R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission,
                    Toast.LENGTH_LONG
                ).show()
            }
            .onAllGranted {
                ShareLogsDialog().show(supportFragmentManager, "Share Logs Dialog")
            }
            .execute()
    }

    // endregion

    private inner class DisplayNameEditActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.activity_settings_display_name_edit_text_hint)
            mode.menuInflater.inflate(R.menu.menu_apply, menu)
            this@SettingsActivity.displayNameEditActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@SettingsActivity.displayNameEditActionMode = null
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.applyButton -> {
                    if (this@SettingsActivity.saveDisplayName()) {
                        mode.finish()
                    }
                    return true
                }
            }
            return false;
        }
    }
}