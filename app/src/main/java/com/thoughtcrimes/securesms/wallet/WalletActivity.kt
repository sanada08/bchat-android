package com.thoughtcrimes.securesms.wallet

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.thoughtcrimes.securesms.data.*
import com.thoughtcrimes.securesms.model.*
import com.thoughtcrimes.securesms.util.Helper
import com.thoughtcrimes.securesms.util.push
import com.thoughtcrimes.securesms.wallet.listener.OnBlockUpdateListener
import com.thoughtcrimes.securesms.wallet.node.NodeFragment
import com.thoughtcrimes.securesms.wallet.receive.ReceiveFragment
import com.thoughtcrimes.securesms.wallet.rescan.RescanDialog
import com.thoughtcrimes.securesms.wallet.scanner.WalletScannerFragment
import com.thoughtcrimes.securesms.wallet.scanner.ScannerFragment
import com.thoughtcrimes.securesms.wallet.send.SendFragment
import com.thoughtcrimes.securesms.wallet.service.WalletService
import com.thoughtcrimes.securesms.wallet.settings.WalletSettings
import com.thoughtcrimes.securesms.wallet.utils.LegacyStorageHelper
import com.thoughtcrimes.securesms.wallet.utils.common.LoadingActivity
import com.thoughtcrimes.securesms.wallet.widget.Toolbar
import io.beldex.bchat.R
import io.beldex.bchat.databinding.ActivityWalletBinding
import io.beldex.bchat.databinding.AlertSyncOptionsBinding
import kotlinx.coroutines.NonCancellable.isCancelled
import timber.log.Timber
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import android.content.DialogInterface
import androidx.core.content.ContextCompat


class WalletActivity : SecureActivity(), WalletFragment.Listener, WalletService.Observer,
    WalletScannerFragment.OnScannedListener, SendFragment.OnScanListener, SendFragment.Listener,
    ReceiveFragment.Listener,WalletFragment.OnScanListener,ScannerFragment.OnWalletScannedListener, WalletScannerFragment.Listener,NodeFragment.Listener{
    lateinit var binding: ActivityWalletBinding


    private var requestStreetMode = false

    private var password: String? = null

    private var uri: String? = null

    private var streetMode: Long = 0
    private var onUriScannedListener: OnUriScannedListener? = null
    private var onUriWalletScannedListener: OnUriWalletScannedListener? = null
    private var barcodeData: BarcodeData? = null

    private val sendFragment: SendFragment? = null

    private val useSSL: Boolean = false
    private val isLightWallet:  Boolean = false




    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // activity restarted
            // we don't want that - finish it and fall back to previous activity
            finish()
            return
        }
        binding = ActivityWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        //Node Connection
        //by hales
        /*if (TextSecurePreferences.getDaemon(this)) {
            TextSecurePreferences.changeDaemon(this,false)
            loadFavouritesWithNetwork()
        }*/


        LegacyStorageHelper.migrateWallets(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        //window.statusBarColor = ContextCompat.getColor(this, R.color.wallet_page_background)

        binding.toolbar.setOnButtonListener { type ->
            when (type) {
                Toolbar.BUTTON_BACK -> {
                    val fragment: Fragment = getCurrentFragment()!!
                    if (fragment is SendFragment || fragment is ReceiveFragment || fragment is ScannerFragment || fragment is WalletScannerFragment) {
                        if (!(fragment as OnBackPressedListener).onBackPressed()) {
                            TextSecurePreferences.callFiatCurrencyApi(this,false)
                            super.onBackPressed()
                        }
                    } else {
                        backToHome()
                    }
                }
                Toolbar.BUTTON_CANCEL -> {
                    if (CheckOnline.isOnline(this)) {
                        onDisposeRequest()
                    }
                    Helper.hideKeyboard(this@WalletActivity)
                    super@WalletActivity.onBackPressed()
                }
                Toolbar.BUTTON_CLOSE -> finish()
                Toolbar.BUTTON_NONE -> Timber.e("Button " + type + "pressed - how can this be?")
                else -> Timber.e("Button " + type + "pressed - how can this be?")
            }
        }

        //showNet(WalletManager.getInstance().networkType)

        val walletFragment: Fragment = WalletFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, walletFragment, WalletFragment::class.java.name).commit()
        Timber.d("fragment added")

        //Important
        //startWalletService()
        Timber.d("onCreate() done.")

        binding.toolbar.toolBarRescan.setOnClickListener {

            val dialog:AlertDialog.Builder = AlertDialog.Builder(this, R.style.BChatAlertDialog_Syncing_Option)
            val li = LayoutInflater.from(dialog.context)
            val promptsView = li.inflate(R.layout.alert_sync_options, null)

            dialog.setView(promptsView)
            val reConnect  = promptsView.findViewById<Button>(R.id.reConnectButton_Alert)
            val reScan = promptsView.findViewById<Button>(R.id.rescanButton_Alert)
            val alertDialog: AlertDialog = dialog.create()
            alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            alertDialog.show()

            reConnect.setOnClickListener {
                if (CheckOnline.isOnline(this)) {
                    onWalletReconnect(node, useSSL, isLightWallet)
                    alertDialog.dismiss()
                } else {
                    Toast.makeText(
                        this,
                        R.string.please_check_your_internet_connection,
                        Toast.LENGTH_SHORT
                    ).show()
                    alertDialog.dismiss()
                }
            }

            reScan.setOnClickListener {
                if (CheckOnline.isOnline(this)) {
                    if (getWallet() != null) {
                        if (isSynced) {
                            if (getWallet()!!.daemonBlockChainHeight != null) {
                                RescanDialog(this, getWallet()!!.daemonBlockChainHeight).show(
                                    supportFragmentManager,
                                    ""
                                )
                            }
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.cannot_rescan_while_wallet_is_syncing),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }

                        //onWalletRescan()
                    }
                } else {
                    Toast.makeText(
                        this@WalletActivity,
                        getString(R.string.please_check_your_internet_connection),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                alertDialog.dismiss()
            }

            // set dialog message
            /*if(CheckOnline.isOnline(this)) {
                if(getWallet()!=null) {
                    if (getWallet()!!.daemonBlockChainHeight != null) {
                        RescanDialog(this, getWallet()!!.daemonBlockChainHeight).show(
                            supportFragmentManager,
                            ""
                        )
                    }
                }
                //onWalletRescan()
            }else{
                Toast.makeText(this@WalletActivity,getString(R.string.please_check_your_internet_connection), Toast.LENGTH_SHORT).show()
            }*/
        }

        binding.toolbar.toolBarSettings.setOnClickListener {
            openWalletSettings()
        }

    }

    override fun onBackPressed() {
        val fragment: Fragment = getCurrentFragment()!!
        if (fragment is SendFragment || fragment is ReceiveFragment || fragment is ScannerFragment || fragment is WalletScannerFragment) {
            if (!(fragment as OnBackPressedListener).onBackPressed()) {
                TextSecurePreferences.callFiatCurrencyApi(this,false)
                super.onBackPressed()
            }
        } else {
            backToHome()
        }
    }

    private fun backToHome() {
        when {
            !synced -> {
                val dialog: AlertDialog.Builder =
                    AlertDialog.Builder(this, R.style.BChatAlertDialog_Wallet_Syncing_Exit_Alert)
                dialog.setTitle(getString(R.string.wallet_syncing_alert_title))
                dialog.setMessage(getString(R.string.wallet_syncing_alert_message))

                dialog.setPositiveButton(R.string.exit) { _, _ ->
                    if (CheckOnline.isOnline(this)) {
                        onDisposeRequest()
                    }
                    setBarcodeData(null)
                    super.onBackPressed()
                }
                dialog.setNegativeButton(R.string.cancel) { _, _ ->
                    // Do nothing
                }
                val alert: AlertDialog = dialog.create()
                alert.show()
                alert.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.text))
                alert.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.alert_ok))
            }
            else -> {
                val dialog: AlertDialog.Builder =
                    AlertDialog.Builder(this, R.style.BChatAlertDialog_Exit)
                dialog.setTitle(getString(R.string.app_exit_alert))

                dialog.setPositiveButton(R.string.exit) { _, _ ->
                    if (CheckOnline.isOnline(this)) {
                        onDisposeRequest()
                    }
                    setBarcodeData(null)
                    super.onBackPressed()
                }
                dialog.setNegativeButton(R.string.cancel) { _, _ ->
                    // Do nothing
                }
                val alert: AlertDialog = dialog.create()
                alert.show()
                alert.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.text))
                alert.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.alert_ok))
            }
        }
    }

    override fun callFinishActivity() {
        if(CheckOnline.isOnline(this)) {
            onDisposeRequest()
        }
        onBackPressed()
    }

    private fun openWalletSettings() {
        /*val intent = Intent(this, WalletSettings::class.java)
        push(intent)*/
        val intent = Intent(this, WalletSettings::class.java)
        resultLauncher.launch(intent)
    }

    fun onWalletRescan(restoreHeight: Long) {
        try {
            val walletFragment = getWalletFragment()

            if(getWallet()!=null) {
                // The height entered by user
                getWallet()!!.restoreHeight = restoreHeight
                getWallet()!!.rescanBlockchainAsync()
            }
            Log.d("Beldex","Restore Height 2 ${getWallet()!!.restoreHeight}")
            synced = false
            walletFragment.unsync()
            invalidateOptionsMenu()
        } catch (ex: java.lang.ClassCastException) {
            Timber.d(ex.localizedMessage)
            // keep calm and carry on
        }
    }

    /*private fun showNet(networkType: NetworkType) {
        when (networkType) {
            NetworkType.NetworkType_Mainnet -> binding.toolbar.setBackgroundResource(R.drawable.card_gradiant_background)
            NetworkType.NetworkType_Stagenet ->{

            }
            NetworkType.NetworkType_Testnet -> binding.toolbar.setBackgroundResource(
                ThemeHelper.getThemedResourceId(
                    this,
                    R.attr.colorPrimaryDark
                )
            )
            else -> throw IllegalStateException(
                "Unsupported Network: " + WalletManager.getInstance().networkType
            )
        }
    }*/

    private fun startWalletService() {

            val extras = intent.extras
            if (extras != null) {
               // acquireWakeLock()
                val walletId = extras.getString(REQUEST_ID)
                // we can set the streetmode height AFTER opening the wallet
                requestStreetMode = extras.getBoolean(REQUEST_STREETMODE)
                password = extras.getString(REQUEST_PW)
                uri = extras.getString(REQUEST_URI)
                if (CheckOnline.isOnline(this)) {
                    Log.d("Beldex", "isOnline 5 if")
                    connectWalletService(walletId, password)
                }
                else {
                    Log.d("Beldex","isOnline 5 else")
                }
            }else {
                finish()
            }
    }

    override fun onBackPressedFun() {
        if(CheckOnline.isOnline(this)) {
            onDisposeRequest()
        }
        setBarcodeData(null)
        onBackPressed()
    }

    private var mBoundService: WalletService? = null
    private var mIsBound = false

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = (service as WalletService.WalletServiceBinder).service
            mBoundService!!.setObserver(this@WalletActivity)
            val extras = intent.extras
            if (extras != null) {
                val walletId = extras.getString(REQUEST_ID)
                if (walletId != null) {
                    //setTitle(walletId, getString(R.string.status_wallet_connecting));
                    //Important
                    //setTitle(getString(R.string.status_wallet_connecting), "")
                   /* setTitle(getString(R.string.my_wallet))*/
                }
            }
            updateProgress()
            Timber.d("CONNECTED")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null
            //setTitle(getString(R.string.wallet_activity_name), getString(R.string.status_wallet_disconnected));
            //Important
            //setTitle(getString(R.string.status_wallet_disconnected), "")
            /*setTitle(getString(R.string.my_wallet))*/
            Log.d("DISCONNECTED", "")
        }
    }

    private fun connectWalletService(walletName: String?, walletPassword: String?) {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Log.d("Beldex","isOnline 7 ${CheckOnline.isOnline(this)}, $walletName , $walletPassword")
        if (CheckOnline.isOnline(this)) {
            var intent: Intent? = null
            if(intent==null) {
                intent = Intent(applicationContext, WalletService::class.java)
                intent.putExtra(WalletService.REQUEST_WALLET, walletName)
                intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_LOAD)
                intent.putExtra(WalletService.REQUEST_CMD_LOAD_PW, walletPassword)
                startService(intent)
                bindService(intent, mConnection, BIND_AUTO_CREATE)
                mIsBound = true
                Timber.d("BOUND")
            }
        }
    }

//////////////////////////////////////////
// WalletFragment.Listener
//////////////////////////////////////////

    // refresh and return true if successful
    override fun hasBoundService(): Boolean {
        return mBoundService != null
    }

    override fun forceUpdate(context: Context) {
        Log.d("Beldex","forceUpdate()")
        try {
            if(getWallet()!=null) {
                Log.d("Beldex","forceUpdate() if")
                Log.d("TransactionList", "full = true -1")
                onRefreshed(getWallet(), true)
            }else{
                Log.d("Beldex","forceUpdate() else")
                if(!CheckOnline.isOnline(this)) {
                    Toast.makeText(
                        context,
                        getString(R.string.please_check_your_internet_connection),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (ex: IllegalStateException) {
            Timber.e(ex.localizedMessage)
        }
    }

    override val connectionStatus: Wallet.ConnectionStatus?
        get() = mBoundService!!.connectionStatus

    override val daemonHeight: Long
        get() = mBoundService!!.daemonHeight

    override fun onSendRequest(view: View?) {
        if(CheckOnline.isOnline(this)) {
            SendFragment.newInstance(uri)
                .let { replaceFragmentWithTransition(view, it, null, null) }
            uri = null // only use uri once
        }else{
            Toast.makeText(this, getString(R.string.please_check_your_internet_connection), Toast.LENGTH_SHORT).show();
        }
    }

    override fun onTxDetailsRequest(view: View?, info: TransactionInfo?) {
        //Important
        /*val args = Bundle()
        args.putParcelable(TxFragment.ARG_INFO, info)
        replaceFragmentWithTransition(view, TxFragment(), null, args)*/
    }

    private var synced = false
    private var streetmode = false

    override val isSynced: Boolean
        get() = synced

    override val streetModeHeight: Long
        get() = streetMode
    override val isWatchOnly: Boolean
        get() = if(getWallet()!=null){getWallet()!!.isWatchOnly}else{false}

    override fun getTxKey(txId: String?): String? {
        return getWallet()!!.getTxKey(txId)
    }

    override fun onWalletReceive(view: View?) {
        replaceFragmentWithTransition(view, ReceiveFragment(), null, null)
        Timber.d("ReceiveFragment placed")
    }

    var haveWallet = false

    override fun hasWallet(): Boolean {
        return haveWallet
    }


    override fun getWallet(): Wallet? {
        return if(mBoundService!=null) {
            checkNotNull(mBoundService) { "WalletService not bound." }
            mBoundService!!.wallet
        }else{
            null
        }
    }

    override fun getStorageRoot(): File {
        TODO("Not yet implemented")
    }

    override fun setToolbarButton(type: Int) {
        binding.toolbar.setButton(type)
    }

    override fun setTitle(title: String?) {
        Timber.d("setTitle:%s.", title)
        binding.toolbar.setTitle(title)
    }

    override fun setTitle(title: String?, subtitle: String?) {
        binding.toolbar.setTitle(title, subtitle)
    }

    override fun setSubtitle(subtitle: String?) {
        binding.toolbar.setSubtitle(subtitle)
    }


    override fun setBarcodeData(data: BarcodeData?) {
        barcodeData = data
    }

    override fun getBarcodeData(): BarcodeData? {
        return barcodeData
    }

    override fun popBarcodeData(): BarcodeData {
        Timber.d("POPPED")
        val data = barcodeData!!
        barcodeData = null
        return data
    }

    override fun setMode(mode: Mode?) {
        if (this.mode != mode) {
            this.mode = mode!!
            when (mode) {
                Mode.BDX -> txData = TxData()
                Mode.BTC -> txData = TxDataBtc()
                else -> throw IllegalArgumentException("Mode " + mode.toString() + " unknown!")
            }
            //Important
            //view!!.post { pagerAdapter.notifyDataSetChanged() }
            Timber.d("New Mode = %s", this.mode.toString())
        }
    }


    override fun getTxData(): TxData? {
        TODO("Not yet implemented")
    }

    private var mode: Mode = Mode.BDX

    /*  override fun setMode(aMode: Mode) {
          if (mode != aMode) {
              mode = aMode
              when (aMode) {
                  Mode.BDX -> txData = TxData()
                  Mode.BTC -> txData = TxDataBtc()
                  else -> throw IllegalArgumentException("Mode " + aMode.toString() + " unknown!")
              }
              //Important
              //view!!.post { pagerAdapter.notifyDataSetChanged() }
              Timber.d("New Mode = %s", mode.toString())
          }
      }*/

    fun getMode(): Mode {
        return mode
    }

    private var txData = TxData()

    enum class Mode {
        BDX, BTC
    }

    private fun getWalletFragment(): WalletFragment {
        return supportFragmentManager.findFragmentByTag(WalletFragment::class.java.name) as WalletFragment
    }

    private fun getCurrentFragment(): Fragment? {
        return supportFragmentManager.findFragmentById(R.id.fragment_container)
    }


//////////////////////////////////////////
// SendFragment.Listener
//////////////////////////////////////////

    override val prefs: SharedPreferences?
        get() = getPreferences(MODE_PRIVATE)
    override val totalFunds: Long
        get() = if(getWallet()!=null){getWallet()!!.unlockedBalance}else{0}
    override val isStreetMode: Boolean
        get() = streetMode > 0


    override fun onPrepareSend(tag: String?, txData: TxData?) {
        if (mIsBound) { // no point in talking to unbound service
            var intent: Intent? = null
            if(intent==null) {
                intent = Intent(applicationContext, WalletService::class.java)
                intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_TX)
                intent.putExtra(WalletService.REQUEST_CMD_TX_DATA, txData)
                intent.putExtra(WalletService.REQUEST_CMD_TX_TAG, tag)
                startService(intent)
                Timber.d("CREATE TX request sent")
            }
            //Important
            /*if (getWallet()!!.deviceType === Wallet.Device.Device_Ledger) showLedgerProgressDialog(
                LedgerProgressDialog.TYPE_SEND
            )*/
        } else {
            Timber.e("Service not bound")
        }
    }
    override val walletName: String?
        get() = getWallet()!!.name

    override fun onSend(notes: UserNotes?) {
        if (mIsBound) { // no point in talking to unbound service
            var intent: Intent? = null
            if(intent==null) {
                intent = Intent(applicationContext, WalletService::class.java)
                intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_SEND)
                intent.putExtra(WalletService.REQUEST_CMD_SEND_NOTES, notes!!.txNotes)
                startService(intent)
                Timber.d("SEND TX request sent")
            }
        } else {
            Timber.e("Service not bound")
        }
    }

    override fun onDisposeRequest() {
        if(getWallet()!=null) {
            getWallet()!!.disposePendingTransaction()
        }
    }

    override fun onFragmentDone() {
        popFragmentStack(null)
    }

    private fun popFragmentStack(name: String?) {
        if (name == null) {
            supportFragmentManager.popBackStack()
        } else {
            supportFragmentManager.popBackStack(name, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }


    override fun setOnUriScannedListener(onUriScannedListener: OnUriScannedListener?) {
        Log.d("Beldex","Value of barcode 5 onUriScannedListener called")
        this.onUriScannedListener = onUriScannedListener
    }

    override fun setOnUriWalletScannedListener(onUriWalletScannedListener: OnUriWalletScannedListener?) {
        Log.d("Beldex","Value of barcode 6 onUriScannedListener called")
        this.onUriWalletScannedListener = onUriWalletScannedListener
    }

    override fun setOnBarcodeScannedListener(onUriScannedListener: OnUriScannedListener?) {
        Log.d("Beldex","Value of barcode 6 onUriScannedListener called")
        this.onUriScannedListener = onUriScannedListener
    }




    override fun onUriScanned(barcodeData: BarcodeData?) {
        super.onUriScanned(barcodeData)
        Log.d("Beldex","Value of barcode 2 $onUriScannedListener")

        var processed = false
        if (onUriScannedListener != null) {

            processed = onUriScannedListener!!.onUriScanned(barcodeData)
        }
        if (!processed || onUriScannedListener == null) {
            Toast.makeText(this, getString(R.string.nfc_tag_read_what), Toast.LENGTH_LONG).show()
        }


    }

    override fun onUriWalletScanned(barcodeData: BarcodeData?) {
        super.onUriWalletScanned(barcodeData)
        var processed = false
        Log.d("Beldex","value of onUriWalletScannedListener $onUriWalletScannedListener")
        if (onUriWalletScannedListener != null) {

            processed = onUriWalletScannedListener!!.onUriWalletScanned(barcodeData)
        }
        if (!processed || onUriWalletScannedListener == null) {
            Toast.makeText(this, getString(R.string.nfc_tag_read_what), Toast.LENGTH_LONG).show()
        }
    }

    private var startScanFragment = false

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (startScanFragment) {
            startScanFragment()
            startScanFragment = false
        }
    }

    private fun startScanFragment() {
        val extras = Bundle()
        replaceFragment(WalletScannerFragment(), null, extras)
    }
    private fun startWalletScanFragment() {
        val extras = Bundle()
        replaceFragment(ScannerFragment(), null, extras)
    }

    /// QR scanner callbacks
    override fun onScan() {
        if (Helper.getCameraPermission(this)) {
            Log.d("Beldex","Called onScan")
            startWalletScanFragment()
        } else {
            Timber.i("Waiting for permissions")
        }
    }

    override fun onWalletScan(view: View?) {
        if (Helper.getCameraPermission(this)) {
            val extras = Bundle()
            Log.d("Beldex","Called onWalletScan")
            replaceFragmentWithTransition(view,WalletScannerFragment(), null, extras)
            //startScanFragment()
        } else {
            Timber.i("Waiting for permissions")
        }

    }

    private fun onWalletReconnect(node: NodeInfo?, UseSSL: Boolean, isLightWallet: Boolean) {
        if (CheckOnline.isOnline(this)) {
            if (getWallet() != null) {
                val isOnline =
                    getWallet()?.reConnectToDaemon(node, UseSSL, isLightWallet) as Boolean
                if (isOnline) {
                    synced = false
                    setNode(node)
                    val walletFragment = getWalletFragment()
                    walletFragment.setProgress(getString(R.string.reconnecting))
                    walletFragment.setProgress(101)
                    invalidateOptionsMenu()
                } else {
                    getWalletFragment().setProgress(R.string.failed_connected_to_the_node)
                }
            } else {
                Toast.makeText(this, "Wait for connection..", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(this, R.string.please_check_your_internet_connection, Toast.LENGTH_SHORT)
                .show()
        }
    }


    override fun onScanned(qrCode: String?): Boolean {
        // #gurke
        val bcData = BarcodeData.fromString(qrCode)
        Log.d("Beldex","Value of barcode 1 $bcData")
        return if (bcData != null) {
            popFragmentStack(null)
            Timber.d("AAA")
            onUriScanned(bcData)
            true
        } else {
            false
        }
    }

    override fun onWalletScanned(qrCode: String?): Boolean {
        // #gurke
        val bcData = BarcodeData.fromString(qrCode)
        Log.d("Beldex","Value of barcode 1 onWalletScanned $bcData")
        return if (bcData != null) {
            popFragmentStack(null)
            Timber.d("AAA")
            onUriWalletScanned(bcData)
            true
        } else {
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Timber.d("onRequestPermissionsResult()")
        if (requestCode == Helper.PERMISSIONS_REQUEST_CAMERA) { // If request is cancelled, the result arrays are empty.
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startScanFragment = true
            } else {
                val msg = getString(R.string.message_camera_not_permitted)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun replaceFragmentWithTransition(
        view: View?,
        newFragment: Fragment,
        stackName: String?,
        extras: Bundle?
    ) {
        Log.d("Beldex", "extras value $extras")
        if (extras != null) {
            Log.d("Beldex", "extras value $extras")
            newFragment.arguments = extras
        }
        /* if (newFragment is TxFragment) transition =
            R.string.tx_details_transition_name else if (newFragment is ReceiveFragment) transition =
            R.string.receive_transition_name else if (newFragment is SendFragment) transition =
            R.string.send_transition_name else if (newFragment is SubaddressInfoFragment) transition =
            R.string.subaddress_info_transition_name else throw IllegalStateException("expecting known transition")
        supportFragmentManager.beginTransaction()
            .addSharedElement(view!!, getString(transition))
            .replace(R.id.fragment_container, newFragment)
            .addToBackStack(stackName)
            .commit()*/

        val transition: Int = if (newFragment is ReceiveFragment)
            R.string.receive_transition_name
        else if (newFragment is SendFragment)
            R.string.send_transition_name
        else if (newFragment is WalletScannerFragment)
            R.string.scan_transition_name
        else throw IllegalStateException("expecting known transition")
        Log.d("Beldex", "extras value transition $transition")

        Log.d("Transition Name ", "${getString(transition)}")
        supportFragmentManager.beginTransaction()
            /*.addSharedElement(view!!, getString(transition))*/
            .replace(R.id.fragment_container, newFragment)
            .addToBackStack(stackName)
            .commit()
    }

    private fun replaceFragment(newFragment: Fragment, stackName: String?, extras: Bundle?) {
        if (extras != null) {
            newFragment.arguments = extras
        }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, newFragment)
            .addToBackStack(stackName)
            .commit()
    }

///////////////////////////
// WalletService.Observer
///////////////////////////

    private var numAccounts = -1

    override fun onRefreshed(wallet: Wallet?, full: Boolean): Boolean {
        Timber.d("onRefreshed()")
        //runOnUiThread { if (getWallet() != null) updateAccountsBalance() }
        if (numAccounts != wallet!!.numAccounts) {
            numAccounts = wallet.numAccounts
            //runOnUiThread { this.updateAccountsList() }
        }
        try {
            Log.d("Beldex","mConnection onRefreshed called ")
            val walletFragment = getWalletFragment()
            if (wallet.isSynchronized) {
                Log.d("Beldex","mConnection onRefreshed called 1")
                //releaseWakeLock(RELEASE_WAKE_LOCK_DELAY) // the idea is to stay awake until synced
                if (!synced) { // first sync
                    Log.d("Beldex","mConnection onRefreshed called 2")
                    onProgress(-2)//onProgress(-1)
                    saveWallet() // save on first sync
                    synced = true
                    runOnUiThread(walletFragment::onSynced)
                }
            }
            runOnUiThread {
                walletFragment.onRefreshed(wallet, full)
                updateCurrentFragment(wallet)
            }
            return true
        } catch (ex: ClassCastException) {
            // not in wallet fragment (probably send monero)
            Timber.d(ex.localizedMessage)
            // keep calm and carry on
        }
        return false
    }


    private fun updateCurrentFragment(wallet: Wallet) {
        val fragment = getCurrentFragment()
        if (fragment is OnBlockUpdateListener) {
            (fragment as OnBlockUpdateListener?)!!.onBlockUpdate(wallet)
        }
    }

    private fun saveWallet() {
        if (mIsBound) { // no point in talking to unbound service
            var intent: Intent? = null
            if(intent==null) {
                intent = Intent(applicationContext, WalletService::class.java)
                intent.putExtra(WalletService.REQUEST, WalletService.REQUEST_CMD_STORE)
                startService(intent)
                Timber.d("STORE request sent")
            }
        } else {
            Timber.e("Service not bound")
        }
    }

    // drawer stuff
    fun updateAccountsBalance() {
        /*val tvBalance: TextView =
            accountsView.getHeaderView(0).findViewById<TextView>(R.id.tvBalance)
        if (!isStreetMode()) {
            tvBalance.text = getString(
                R.string.accounts_balance,
                Helper.getDisplayAmount(getWallet()!!.balanceAll, 5)
            )
        } else {
            tvBalance.text = null
        }
        updateAccountsList()*/
    }

    fun updateAccountsList() {
        /*val menu: Menu = accountsView.getMenu()
        menu.removeGroup(R.id.accounts_list)
        val wallet = getWallet()
        if (wallet != null) {
            val n = wallet.numAccounts
            val showBalances = n > 1 && !isStreetMode()
            for (i in 0 until n) {
                val label = if (showBalances) getString(
                    R.string.label_account,
                    wallet.getAccountLabel(i),
                    Helper.getDisplayAmount(wallet.getBalance(i), 2)
                ) else wallet.getAccountLabel(i)
                val item = menu.add(R.id.accounts_list, getAccountId(i), 2 * i, label)
                item.setIcon(R.drawable.ic_account_balance_wallet_black_24dp)
                if (i == wallet.accountIndex) item.isChecked = true
            }
            menu.setGroupCheckable(R.id.accounts_list, true, true)
        }*/
    }

    override fun onProgress(text: String?) {
        try {
            val walletFragment = getWalletFragment()
            runOnUiThread { walletFragment.setProgress(text) }
        } catch (ex: ClassCastException) {
            // not in wallet fragment (probably send beldex)
            Timber.d(ex.localizedMessage)
            // keep calm and carry on
        }
    }

    override fun onProgress(n: Int) {
        runOnUiThread {
            try {
                val walletFragment: WalletFragment = getWalletFragment()
                if (walletFragment != null) walletFragment.setProgress(n)
            } catch (ex: ClassCastException) {
                // not in wallet fragment (probably send monero)
                Timber.d(ex.localizedMessage)
                // keep calm and carry on
            }
        }
    }

    private fun updateProgress() {
        Log.d("Beldex","mConnection called updateProgress() 1")
        if (hasBoundService()) {
            Log.d("Beldex","mConnection called updateProgress() 2")
            Log.d("Beldex","mConnection called updateProgress() 2 1 $mBoundService")
            onProgress(mBoundService!!.progressText)
            onProgress(mBoundService!!.progressValue)
        }
        //No internet
       /* else{
            onProgress("Failed to node")
            onProgress(0)
        }*/
    }

    override fun onWalletStored(success: Boolean) {
        runOnUiThread {
            if (success) {
                Toast.makeText(
                    this@WalletActivity,
                    getString(R.string.status_wallet_unloaded),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@WalletActivity,
                    getString(R.string.status_wallet_unload_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onTransactionCreated(
        txTag: String,
        pendingTransaction: PendingTransaction
    ) {
        try {
            val sendFragment = getCurrentFragment() as SendFragment?
            runOnUiThread {
                //dismissProgressDialog()
                val status = pendingTransaction.status
                Log.d("onTransactionCreated:","$status")
                Log.d("transaction status 1 i %s", status.toString())
                if (status !== PendingTransaction.Status.Status_Ok) {
                    Log.d("onTransactionCreated not Status_Ok","-")
                    val errorText = pendingTransaction!!.errorString
                    getWallet()!!.disposePendingTransaction()
                    sendFragment!!.onCreateTransactionFailed(errorText)
                } else {
                    Log.d("transaction status 1 ii %s", status.toString())
                    Log.d("onTransactionCreated Status_Ok","-")
                    sendFragment!!.onTransactionCreated("txTag", pendingTransaction)
                }
            }
        } catch (ex: ClassCastException) {
            // not in spend fragment
            Timber.d(ex.localizedMessage)
            // don't need the transaction any more
            if(getWallet()!=null) {
                getWallet()!!.disposePendingTransaction()
            }
        }
    }

    override fun onTransactionSent(txId: String?) {
        try {
            val sendFragment = getCurrentFragment() as SendFragment?
            runOnUiThread { sendFragment!!.onTransactionSent(txId) }
        } catch (ex: ClassCastException) {
            // not in spend fragment
            Timber.d(ex.localizedMessage)
        }
    }

    override fun onSendTransactionFailed(error: String?) {
        try {
            val sendFragment = getCurrentFragment() as SendFragment?
            runOnUiThread { sendFragment!!.onSendTransactionFailed(error) }
        } catch (ex: ClassCastException) {
            // not in spend fragment
            Timber.d(ex.localizedMessage)
        }
    }

    override fun onWalletStarted(walletStatus: Wallet.Status?) {
        Log.d("Beldex", "Wallet start called 7 0 ")
        runOnUiThread {
            dismissProgressDialog()
            if (walletStatus == null) {
                // guess what went wrong
                Toast.makeText(
                    this@WalletActivity,
                    getString(R.string.status_wallet_connect_failed),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                if (Wallet.ConnectionStatus.ConnectionStatus_WrongVersion === walletStatus.connectionStatus) {
                    Toast.makeText(
                    this@WalletActivity,
                    getString(R.string.status_wallet_connect_wrong_version),
                    Toast.LENGTH_LONG
                ).show() }else if (!walletStatus.isOk) {
                    Timber.d("Not Ok %s", walletStatus.toString())
                    if(walletStatus.errorString.isNotEmpty()) {
                        Toast.makeText(
                            this@WalletActivity,
                            walletStatus.errorString,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        if (walletStatus == null || Wallet.ConnectionStatus.ConnectionStatus_Connected !== walletStatus.connectionStatus) {
            Log.d("Beldex","WalletActivity finished called")
            finish()
        } else {
            Log.d("Beldex", "Wallet start called 7 1 ")
            haveWallet = true
            invalidateOptionsMenu()
            if (requestStreetMode) onEnableStreetMode()
            Log.d("Beldex", "Wallet start called 7 2 ")
            val walletFragment = getWalletFragment()
            Log.d("Beldex", "Wallet start called 7  $walletFragment")
            runOnUiThread {
                updateAccountsHeader()
                if (walletFragment != null) {
                    Log.d("Beldex", "Wallet start called 8 ")
                    walletFragment.onLoaded()
                }
            }
        }
    }

    private fun updateAccountsHeader() {
        /*val wallet = getWallet()
        val tvName: TextView = accountsView.getHeaderView(0).findViewById<TextView>(R.id.tvName)
        tvName.text = wallet.name*/
    }

    private fun updateStreetMode() {
        invalidateOptionsMenu()
    }

    private fun onEnableStreetMode() {
        enableStreetMode(true)
        updateStreetMode()
    }

    private fun enableStreetMode(enable: Boolean) {
        if (enable) {
            if(getWallet()!=null) {
               streetMode= getWallet()!!.daemonBlockChainHeight
            }
        } else {
            streetMode = 0
        }
        val walletFragment = getWalletFragment()
        if (walletFragment != null) walletFragment.resetDismissedTransactions()
        forceUpdate(this)
        //runOnUiThread { if (getWallet() != null) updateAccountsBalance() }
    }

    override fun onWalletOpen(device: Wallet.Device?) {
        //Important
        /*if (device === Wallet.Device.Device_Ledger) {
            runOnUiThread { showLedgerProgressDialog(LedgerProgressDialog.TYPE_RESTORE) }
        }*/
    }

    //Node Connection

    private val NODES_PREFS_NAME = "nodes"
    private val SELECTED_NODE_PREFS_NAME = "selected_node"
    private val PREF_DAEMON_TESTNET = "daemon_testnet"
    private val PREF_DAEMON_STAGENET = "daemon_stagenet"
    private val PREF_DAEMON_MAINNET = "daemon_mainnet"
    private var favouriteNodes: MutableSet<NodeInfo> = HashSet<NodeInfo>()

    private var node: NodeInfo? = null

    ////////////////////////////////////////
    ////////////////////////////////////////
    /*override fun showNet() {
        //showNet(WalletManager.getInstance().networkType)
    }*/

    private fun checkServiceRunning(): Boolean {
        return if (WalletService.Running) {
            Toast.makeText(this, getString(R.string.service_busy), Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
    }

    override fun onNodePrefs() {
        Timber.d("node prefs")
        if (checkServiceRunning()) return
        startNodeFragment()
    }

   /* override fun hiddenRescan(status: Boolean) {
        binding.toolbar.hiddenRescan(status)
    }*/

    private fun startNodeFragment() {
        //Important
        //replaceFragment(NodeFragment(), null, null)
        Timber.d("NodeFragment placed")
    }

    override fun getNode(): NodeInfo? {
        return if(TextSecurePreferences.getDaemon(this)){
            TextSecurePreferences.changeDaemon(this,false)
            val selectedNodeId = getSelectedNodeId()
            val nodeInfo = NodeInfo.fromString(selectedNodeId)
            Log.d("Beldex","value of node 1 $selectedNodeId")
            Log.d("Beldex","value of node 2 $nodeInfo")


            nodeInfo
        }else {
            node
        }
    }

    override fun setNode(node: NodeInfo?) {
        Log.d("Beldex", "Value of current Setnode called in WalletActivity ${node?.host}")
        setNode(node, true)
    }

    private fun setNode(node: NodeInfo?, save: Boolean) {
        Log.d("Beldex","Value of getNode in WalletActivity ${getNode()?.host}")
        Log.d("Beldex","Value of current node in WalletActivity ${node?.host}")
        Log.d("Beldex","Value of current node in WalletActivity 1 ${this.node?.host}")
        if (node !== this.node) {
            require(!(node != null && node.networkType !== WalletManager.getInstance().networkType)) { "network type does not match" }
            this.node = node
            for (nodeInfo in favouriteNodes) {
                Log.d("Testing-->14 ","${node.toString()}")
                nodeInfo.isSelected = nodeInfo === node
            }
            WalletManager.getInstance().setDaemon(node)
            if (save) saveSelectedNode()

            //SteveJosephh21
            startWalletService()
        }//No Internet
        /*else{

        }*/
    }

    private fun loadFavouritesWithNetwork() {
        Helper.runWithNetwork {
            loadFavourites()
            true
        }
    }

    override fun getFavouriteNodes(): MutableSet<NodeInfo> {
        return favouriteNodes
    }

    override fun getOrPopulateFavourites(): MutableSet<NodeInfo> {
        Log.d("Beldex","getOrPopulateFavourites() fun called ${DefaultNodes.values()}")
        if (favouriteNodes.isEmpty()) {
            for (node in DefaultNodes.values()) {
                val nodeInfo = NodeInfo.fromString(node.uri)
                if (nodeInfo != null) {
                    nodeInfo.setFavourite(true)
                    favouriteNodes.add(nodeInfo)
                }
            }
            saveFavourites()
        }
        return favouriteNodes
    }

    override fun setFavouriteNodes(nodes: MutableCollection<NodeInfo>?) {
        Timber.d("adding %d nodes", nodes!!.size)
        favouriteNodes.clear()
        for (node in nodes) {
            /*Log.d("adding %s %b", node, node.isFavourite)*/
            if (node.isFavourite) favouriteNodes.add(node)
        }
        saveFavourites()
    }

    private fun getSelectedNodeId(): String? {
        return getSharedPreferences(
            SELECTED_NODE_PREFS_NAME,
            MODE_PRIVATE
        ).getString("0", null)
    }

    private fun saveFavourites() {
        Timber.d("SAVE")
        val editor = getSharedPreferences(
            NODES_PREFS_NAME,
            MODE_PRIVATE
        ).edit()
        editor.clear()
        var i = 1
        for (info in favouriteNodes) {
            val nodeString = info.toNodeString()
            editor.putString(i.toString(), nodeString)
            Timber.d("saved %d:%s", i, nodeString)
            i++
        }
        editor.apply()
    }

    private fun addFavourite(nodeString: String): NodeInfo? {
        Log.d("Beldex","Value of current node loadFav called AddFav 1")
        val nodeInfo = NodeInfo.fromString(nodeString)
        if (nodeInfo != null) {
            Log.d("Beldex","Value of current node loadFav called AddFav 2")
            nodeInfo.setFavourite(true)
            favouriteNodes.add(nodeInfo)
        } else Timber.w("nodeString invalid: %s", nodeString)
        Log.d("Beldex","Value of current node loadFav called AddFav 3")
        return nodeInfo
    }

    private fun loadLegacyList(legacyListString: String?) {
        if (legacyListString == null) return
        val nodeStrings = legacyListString.split(";".toRegex()).toTypedArray()
        for (nodeString in nodeStrings) {
            addFavourite(nodeString)
        }
    }

    private fun saveSelectedNode() { // save only if changed
        val nodeInfo = getNode()
        Log.d("Beldex","Value of getNode ${nodeInfo?.host}")
        val selectedNodeId = getSelectedNodeId()
        if (nodeInfo != null) {
            if (!nodeInfo.toNodeString().equals(selectedNodeId)) saveSelectedNode(nodeInfo)
        } else {
            if (selectedNodeId != null) saveSelectedNode(null)
        }
    }

    private fun saveSelectedNode(nodeInfo: NodeInfo?) {
        val editor = getSharedPreferences(
            SELECTED_NODE_PREFS_NAME,
            MODE_PRIVATE
        ).edit()
        if (nodeInfo == null) {
            editor.clear()
        } else {
            editor.putString("0", getNode()!!.toNodeString())
        }
        editor.apply()
    }

    private fun loadFavourites() {
        Timber.d("loadFavourites")
        Log.d("Beldex","Value of current node loadFav called")
        favouriteNodes.clear()
        val selectedNodeId = getSelectedNodeId()
        Log.d("Beldex"," Value of current node selectedNodeID $selectedNodeId")
        val storedNodes = getSharedPreferences(NODES_PREFS_NAME, MODE_PRIVATE).all

        for (nodeEntry in storedNodes.entries) {
            Log.d("Beldex","Value of current node loadFav called 1")
            if (nodeEntry != null) { // just in case, ignore possible future errors
                Log.d("Beldex","Value of current node loadFav called 2")
                val nodeId = nodeEntry.value as String
                val addedNode: NodeInfo = addFavourite(nodeId)!!
                if (addedNode != null) {
                    Log.d("Beldex","Value of current node loadFav called 3 nodeId $nodeId")
                    Log.d("Beldex","Value of current node loadFav called 3 selectedNodeId $selectedNodeId")
                    if (nodeId == selectedNodeId) {
                        Log.d("Beldex","Value of current node loadFav called 3.1 if")
                        addedNode.isSelected = true
                    }
                }
            }
        }
        if (storedNodes.isEmpty()) { // try to load legacy list & remove it (i.e. migrate the data once)
            Log.d("Beldex","Value of current node loadFav called 4")
            val sharedPref = getPreferences(MODE_PRIVATE)
            when (WalletManager.getInstance().networkType) {
                NetworkType.NetworkType_Mainnet -> {
                    Log.d("Beldex","Value of current node loadFav called 5")
                    loadLegacyList(
                        sharedPref.getString(
                            PREF_DAEMON_MAINNET,
                            null
                        )
                    )
                    sharedPref.edit().remove(PREF_DAEMON_MAINNET)
                        .apply()
                }
                NetworkType.NetworkType_Stagenet -> {
                    Log.d("Beldex","Value of current node loadFav called 6")
                    loadLegacyList(
                        sharedPref.getString(
                            PREF_DAEMON_STAGENET,
                            null
                        )
                    )
                    sharedPref.edit()
                        .remove(PREF_DAEMON_STAGENET).apply()
                }
                NetworkType.NetworkType_Testnet -> {
                    Log.d("Beldex","Value of current node loadFav called 7")
                    loadLegacyList(
                        sharedPref.getString(
                            PREF_DAEMON_TESTNET,
                            null
                        )
                    )
                    sharedPref.edit().remove(PREF_DAEMON_TESTNET)
                        .apply()
                }
                else -> throw IllegalStateException("unsupported net " + WalletManager.getInstance().networkType)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
    }

    override fun onResume() {
        super.onResume()
        Log.d("Beldex","Value of change daemon ${TextSecurePreferences.getDaemon(this)}")
        Timber.d("onResume()-->")
        // wait for WalletService to finish
        //Important
        /*if (WalletService.Running && progressDialog == null) {
            // and show a progress dialog, but only if there isn't one already
            //AsyncWaitForService().execute()
             AsyncWaitForService(this).execute<Int>()
        }*/
        //Important
        //if (!Ledger.isConnected()) attachLedger()
        if(!CheckOnline.isOnline(this)){
            Toast.makeText(this,getString(R.string.please_check_your_internet_connection),Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        dismissProgressDialog()
        //Important
        //unregisterDetachReceiver()
        //Ledger.disconnect()

        if(CheckOnline.isOnline(this)) {
            if (mBoundService != null && getWallet() != null) {
                saveWallet()
            }
        }
        stopWalletService()
        super.onDestroy()
    }

    private fun stopWalletService() {
        disconnectWalletService()
        //releaseWakeLock()
    }

    fun disconnectWalletService() {
        if (mIsBound) {
            Log.d("Beldex","mIsBound called if ")
            // Detach our existing connection.
            mBoundService!!.setObserver(null)
            unbindService(mConnection)
            mIsBound = false
            Timber.d("UNBOUND")
        }
    }

    var detachReceiver: BroadcastReceiver? = null

    private fun unregisterDetachReceiver() {
        if (detachReceiver != null) {
            unregisterReceiver(detachReceiver)
            detachReceiver = null
        }
    }

    private class AsyncWaitForService(val walletActivity: WalletActivity) :
        AsyncTaskCoroutine<Int?, NodeInfo?>() {
        override fun onPreExecute() {
            super.onPreExecute()
            walletActivity.showProgressDialog(R.string.service_progress)
        }

        override fun onPostExecute(result: NodeInfo?) {
            super.onPostExecute(result)
            if (walletActivity.isDestroyed) {
                Timber.d("Exception-->3")
                return
            }
            walletActivity.dismissProgressDialog()
        }

        override fun doInBackground(vararg params: Int?): NodeInfo? {
            try {
                while (WalletService.Running and !isCancelled) {
                    Timber.d("Exception-->1")
                    Thread.sleep(250)
                }
            } catch (ex: InterruptedException) {
                Timber.d("Exception-->2")
                // oh well ...
            }
            return null
        }
    }

    companion object {

        var REQUEST_URI = "uri"
        var REQUEST_ID = "id"
        var REQUEST_PW = "pw"
        var REQUEST_FINGERPRINT_USED = "fingerprint"
        var REQUEST_STREETMODE = "streetmode"
    }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, LoadingActivity::class.java)
            push(intent)
            finish()
        }
    }
}