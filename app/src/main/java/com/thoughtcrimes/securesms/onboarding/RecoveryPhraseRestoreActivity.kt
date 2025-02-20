package com.thoughtcrimes.securesms.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import io.beldex.bchat.R
import io.beldex.bchat.databinding.ActivityRecoveryPhraseRestoreBinding
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.beldex.libsignal.crypto.MnemonicCodec
import com.thoughtcrimes.securesms.BaseActionBarActivity
import com.thoughtcrimes.securesms.crypto.IdentityKeyUtil
import com.thoughtcrimes.securesms.crypto.KeyPairUtilities
import com.thoughtcrimes.securesms.crypto.MnemonicUtilities
import com.thoughtcrimes.securesms.util.push
import com.thoughtcrimes.securesms.util.setUpActionBarBchatLogo
import com.thoughtcrimes.securesms.seed.RecoveryGetSeedDetailsActivity
import com.beldex.libsignal.utilities.*


class RecoveryPhraseRestoreActivity : BaseActionBarActivity() {
    private lateinit var binding: ActivityRecoveryPhraseRestoreBinding
    var filter: InputFilter?=null
    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarBchatLogo("Restore from Seed",true)
        TextSecurePreferences.apply {
            setHasViewedSeed(this@RecoveryPhraseRestoreActivity, true)
            setConfigurationMessageSynced(this@RecoveryPhraseRestoreActivity, false)
            setRestorationTime(this@RecoveryPhraseRestoreActivity, System.currentTimeMillis())
            setLastProfileUpdateTime(this@RecoveryPhraseRestoreActivity, System.currentTimeMillis())
        }
        binding = ActivityRecoveryPhraseRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mnemonicEditText.imeOptions = binding.mnemonicEditText.imeOptions or 16777216 // Always use incognito keyboard
        binding.restoreButton.setOnClickListener {
            if(binding.recoveryPhraseCountWord.text!=null && binding.recoveryPhraseCountWord.text=="25/25") {
                restore()
            }
            else{
                Toast.makeText(this,"Please enter valid seed",Toast.LENGTH_SHORT).show()
            }
        }
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms of Service and Privacy Policy")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://www.beldex.io/")
            }
        }, 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://www.beldex.io/")
            }
        }, 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.termsTextView.text = termsExplanation

        binding.mnemonicEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                var count : Int = 1

                if(s.toString().isNotEmpty())

                    for(i in 0..s.toString().length-1){

                        if(s.toString()[i].toString() == " "){
                            count++
                        }

                        if(i > 1 && s.toString()[i-1].toString() == " " && s.toString()[i].toString() == " "){
                            count--
                        }

                    }
                binding.recoveryPhraseCountWord.text = "$count/25"

                if (count >= 25){
                    filter = InputFilter.LengthFilter(binding.mnemonicEditText.text.toString().length)
                    binding.mnemonicEditText.filters = arrayOf<InputFilter>(filter ?: return)
                }
                else if (filter != null) {
                    binding.mnemonicEditText.filters = arrayOfNulls(0)
                    filter = null

                }
            }
        })

        binding.clearButton.setOnClickListener {
            binding.mnemonicEditText.text.clear()
            binding.recoveryPhraseCountWord.text= "0/25"
        }

       /* binding.recoveryPhrasePasteIcon?.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            binding.mnemonicEditText.setText(clipboard.text.toString())
        }*/

        binding.recoveryPhrasePasteIcon.setOnClickListener {
            val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            //since the clipboard contains plain text.
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip!!.getItemAt(0)
                // Gets the clipboard as text.
                binding.mnemonicEditText.setText(item.text.toString())
            } else {
                Toast.makeText(this, R.string.no_copied_seed, Toast.LENGTH_SHORT)
                    .show()
            }

        }
    }

    // endregion

    // region Interaction
    private fun restore() {
        val mnemonic = binding.mnemonicEditText.text.toString()
        try {
            val loadFileContents: (String) -> String = { fileName ->
                MnemonicUtilities.loadFileContents(this, fileName)
            }
            val hexEncodedSeed = MnemonicCodec(loadFileContents).decode(mnemonic)
            val seed = Hex.fromStringCondensed(hexEncodedSeed)
            val keyPairGenerationResult = KeyPairUtilities.generate(seed)
            val x25519KeyPair = keyPairGenerationResult.x25519KeyPair
            KeyPairUtilities.store(this, seed, keyPairGenerationResult.ed25519KeyPair, x25519KeyPair)
            val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(this, registrationID)
            TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
            val intent = Intent(this, RecoveryGetSeedDetailsActivity::class.java)
            intent.putExtra("seed",seed1)
            push(intent)
            finish()
            // Important
            /*val intent = Intent(this, DisplayNameActivity::class.java)
            push(intent)*/
        } catch (e: Exception) {
            val message = if (e is MnemonicCodec.DecodingError) e.description else MnemonicCodec.DecodingError.Generic.description
            return Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val seed1 by lazy {
        var hexEncodedSeed = IdentityKeyUtil.retrieve(this, IdentityKeyUtil.BELDEX_SEED)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(this).hexEncodedPrivateKey // Legacy account
        }
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(this, fileName)
        }
        MnemonicCodec(loadFileContents).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    private fun openURL(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }
    // endregion
}