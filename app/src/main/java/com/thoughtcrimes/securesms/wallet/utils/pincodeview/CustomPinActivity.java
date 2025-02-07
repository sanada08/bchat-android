package com.thoughtcrimes.securesms.wallet.utils.pincodeview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.beldex.libbchat.utilities.TextSecurePreferences;
import com.thoughtcrimes.securesms.wallet.WalletActivity;
import com.thoughtcrimes.securesms.wallet.utils.pincodeview.managers.AppLockActivity;

import io.beldex.bchat.R;


public class CustomPinActivity extends AppLockActivity {

    @Override
    public void showForgotDialog() {
       /* Resources res = getResources();
        // Create the builder with required paramaters - Context, Title, Positive Text
        CustomDialog.Builder builder = new CustomDialog.Builder(this,
                res.getString(R.string.activity_dialog_title),
                res.getString(R.string.activity_dialog_accept));
        builder.content(res.getString(R.string.activity_dialog_content));
        builder.negativeText(res.getString(R.string.activity_dialog_decline));

        //Set theme
        builder.darkTheme(false);
        builder.typeface(Typeface.SANS_SERIF);
        builder.positiveColor(res.getColor(R.color.light_blue_500)); // int res, or int colorRes parameter versions available as well.
        builder.negativeColor(res.getColor(R.color.light_blue_500));
        builder.rightToLeft(false); // Enables right to left positioning for languages that may require so.
        builder.titleAlignment(BaseDialog.Alignment.CENTER);
        builder.buttonAlignment(BaseDialog.Alignment.CENTER);
        builder.setButtonStacking(false);

        //Set text sizes
        builder.titleTextSize((int) res.getDimension(R.dimen.activity_dialog_title_size));
        builder.contentTextSize((int) res.getDimension(R.dimen.activity_dialog_content_size));
        builder.positiveButtonTextSize((int) res.getDimension(R.dimen.activity_dialog_positive_button_size));
        builder.negativeButtonTextSize((int) res.getDimension(R.dimen.activity_dialog_negative_button_size));

        //Build the dialog.
        CustomDialog customDialog = builder.build();
        customDialog.setCanceledOnTouchOutside(false);
        customDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        customDialog.setClickListener(new CustomDialog.ClickListener() {
            @Override
            public void onConfirmClick() {
                Toast.makeText(getApplicationContext(), "Yes", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelClick() {
                Toast.makeText(getApplicationContext(), "Cancel", Toast.LENGTH_SHORT).show();
            }
        });

        // Show the dialog.
        customDialog.show();*/
    }

    @Override
    public void onPinFailure(int attempts) {
        Log.d(TAG,"onPinFailure");
    }

    @Override
    public void onPinSuccess(int attempts, int pinLockStatus, AppLockActivity appLockActivity) {
        if(pinLockStatus==3) {
            setUpPinPopUp(appLockActivity,true,this);
        }
        else if(pinLockStatus== 7){
            setUpPinPopUp(appLockActivity,false, this);
        }
        else if(pinLockStatus == 4){
            getWalletValuesFromSharedPreferences(this);
        }
        else if(pinLockStatus==6){
            Intent returnIntent = new Intent();
            setResult(Activity.RESULT_OK,returnIntent);
        }

        Log.d(TAG,"onPinSuccess "+attempts);
    }

    private void setUpPinPopUp(AppLockActivity appLockActivity, boolean status, CustomPinActivity customPinActivity){
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.setup_pin_success, null);

        dialog.setView(dialogView);

        Button okButton = dialogView.findViewById(R.id.okButton);
        TextView title = dialogView.findViewById(R.id.setUpPinSuccessTitle);
        if(status){
            title.setText(R.string.your_pin_has_been_set_up_successfully);
        }else{
            title.setText(R.string.your_pin_has_been_changed_successfully);
        }

        AlertDialog alert = dialog.create();
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        alert.setCanceledOnTouchOutside(false);
        alert.show();

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismiss();
                appLockActivity.finish();
                if(status){
                    getWalletValuesFromSharedPreferences(customPinActivity);
                }
            }
        });

    }

    private void getWalletValuesFromSharedPreferences(CustomPinActivity customPinActivity){
        String walletName = TextSecurePreferences.getWalletName(customPinActivity);
        String walletPassword = TextSecurePreferences.getWalletPassword(customPinActivity);
        if (walletName != null && walletPassword !=null) {
            startWallet(walletName, walletPassword,  false,  false);
        }
    }

    private void startWallet(
            String walletName, String walletPassword,
            Boolean fingerprintUsed, Boolean streetmode) {
        String REQUEST_ID = "id";
        String REQUEST_PW = "pw";
        String REQUEST_FINGERPRINT_USED = "fingerprint";
        String REQUEST_STREETMODE = "streetmode";
        String REQUEST_URI = "uri";

        Log.d("startWallet()","");
        TextSecurePreferences.callFiatCurrencyApi(this,true);
        TextSecurePreferences.setIncomingTransactionStatus(this, true);
        TextSecurePreferences.setOutgoingTransactionStatus(this, true);
        TextSecurePreferences.setTransactionsByDateStatus(this,false);
        Intent intent =new Intent(this, WalletActivity.class);
        intent.putExtra(REQUEST_ID, walletName);
        intent.putExtra(REQUEST_PW, walletPassword);
        intent.putExtra(REQUEST_FINGERPRINT_USED, fingerprintUsed);
        intent.putExtra(REQUEST_STREETMODE, streetmode);
        //Important
        /*if (uri != null) {
            intent.putExtra(REQUEST_URI, uri)
            uri = null // use only once
        }*/
        startActivity(intent);
    }

    @Override
    public int getPinLength() {
        return super.getPinLength();//you can override this method to change the pin length from the default 4
    }
}
