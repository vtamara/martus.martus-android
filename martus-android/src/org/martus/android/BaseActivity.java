package org.martus.android;

import java.io.File;

import org.martus.android.dialog.ConfirmationDialog;
import org.martus.android.dialog.InstallExplorerDialog;
import org.martus.android.dialog.LoginRequiredDialog;
import org.martus.common.MartusUtilities;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.crypto.MartusKeyPair;
import org.martus.common.crypto.MartusSecurity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

/**
 * @author roms
 *         Date: 12/19/12
 */
public class BaseActivity extends FragmentActivity implements ConfirmationDialog.ConfirmationDialogListener,
        LoginRequiredDialog.LoginRequiredDialogListener {

    private static final long MINUTE_MILLIS = 60000;

    public static final int EXIT_RESULT_CODE = 10;
    public static final int EXIT_REQUEST_CODE = 10;
    public static final String PREFS_DESKTOP_KEY = "desktopHQ";
    protected static final String PREFS_DIR = "shared_prefs";

    protected MartusApplication parentApp;
    private String confirmationDialogTitle;
    protected ProgressDialog dialog;
    SharedPreferences mySettings;

    private Handler inactivityHandler;
    private Runnable inactivityCallback;
    private long inactivityTimeout;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parentApp = (MartusApplication) this.getApplication();
        confirmationDialogTitle = getString(R.string.confirm_default);
        mySettings = PreferenceManager.getDefaultSharedPreferences(this);
        inactivityHandler = new EmptyHandler();
        inactivityCallback = new LogOutProcess(this);
        int timeoutSetting = Integer.valueOf(mySettings.getString(SettingsActivity.KEY_TIMEOUT, "7"));
        Log.e(AppConfig.LOG_LABEL, "TIMEOUT IS " + timeoutSetting);
        inactivityTimeout = timeoutSetting * MINUTE_MILLIS;
    }

    public void resetInactivityTimer(){
        Log.w(AppConfig.LOG_LABEL, "start resetInactivityTimer");
        inactivityHandler.removeCallbacks(inactivityCallback);
        if (!MartusApplication.isIgnoreInactivity()) {
            Log.w(AppConfig.LOG_LABEL, "is not ignore in resetInactivityTimer");
            inactivityHandler.postDelayed(inactivityCallback, inactivityTimeout);
        } else {
            Log.w(AppConfig.LOG_LABEL, "is ignore in resetInactivityTimer");
        }
    }

    public void stopInactivityTimer(){
        Log.w(AppConfig.LOG_LABEL, "start stopInactivityTimer");
        inactivityHandler.removeCallbacks(inactivityCallback);
    }

    public void showLoginRequiredDialog() {
        LoginRequiredDialog loginRequiredDialog = LoginRequiredDialog.newInstance();
        loginRequiredDialog.show(getSupportFragmentManager(), "dlg_login");
    }


    public void onFinishLoginRequiredDialog() {
        BaseActivity.this.finish();
        Intent intent = new Intent(BaseActivity.this, MartusActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(MartusActivity.RETURN_TO, MartusActivity.ACTIVITY_BULLETIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    public void showInstallExplorerDialog() {
        InstallExplorerDialog explorerDialog = InstallExplorerDialog.newInstance();
        explorerDialog.show(getSupportFragmentManager(), "dlg_install");
    }

    public void showConfirmationDialog() {
        ConfirmationDialog confirmationDialog = ConfirmationDialog.newInstance();
        confirmationDialog.show(getSupportFragmentManager(), "dlg_confirmation");
    }

    public void onConfirmationAccepted() {
        //do nothing
    }

    public void onConfirmationCancelled() {
        //do nothing
    }

    @Override
    public String getConfirmationTitle() {
        return confirmationDialogTitle;
    }

    @Override
    public String getConfirmationMessage() {
        return "";
    }

    @Override
    public void onUserInteraction(){
        resetInactivityTimer();
    }

    @Override
    public void onResume() {
        super.onResume();
        resetInactivityTimer();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopInactivityTimer();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //No call for super(). Bug on API Level > 11.
    }

    protected void showMessage(Context context, String msg, String title){
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setIcon(android.R.drawable.ic_dialog_alert)
             .setTitle(title)
             .setMessage(msg)
             .setPositiveButton(R.string.alert_dialog_ok, new SimpleOkayButtonListener())
             .show();
    }

    public class SimpleOkayButtonListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            //do nothing
        }
    }

    protected boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    public void close() {
        setResult(EXIT_RESULT_CODE);
        finish();
    }

    protected MartusCrypto getSecurity()
    {
        return AppConfig.getInstance().getCrypto();
    }

    protected void verifySavedDesktopKeyFile() throws MartusUtilities.FileVerificationException {
        File desktopKeyFile = getDesktopKeyFile();
        if (desktopKeyFile.exists()) {
            File sigFile = new File(desktopKeyFile.getParent(), desktopKeyFile.getName() + ".sig");
            MartusUtilities.verifyFileAndSignature(desktopKeyFile, sigFile, getSecurity(), getSecurity().getPublicKeyString());
        }
    }

    protected File getDesktopKeyFile() {
        File prefsDir = new File(getCacheDir().getParent(), PREFS_DIR);
        return new File(prefsDir, PREFS_DESKTOP_KEY + ".xml");
    }

    public static MartusSecurity createKeyPairCopy(MartusSecurity original) {
        MartusSecurity cryptoCopy = null;
        try {
            MartusKeyPair keyPair = original.getKeyPair();
            byte[] data = keyPair.getKeyPairData();
            cryptoCopy = new MartusSecurity();
            cryptoCopy.setKeyPairFromData(data);
            cryptoCopy.setShouldWriteAuthorDecryptableData(false);
        } catch (Exception e) {
            Log.e(AppConfig.LOG_LABEL, "Problem copying crypto", e);
        }
        return cryptoCopy;
    }

    protected void showProgressDialog(String title) {
        dialog = new ProgressDialog(this);
        dialog.setTitle(title);
        dialog.setIndeterminate(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}