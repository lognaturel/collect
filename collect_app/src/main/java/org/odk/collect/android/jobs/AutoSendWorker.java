package org.odk.collect.android.jobs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.NotificationActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.InstanceGoogleSheetsUploader;
import org.odk.collect.android.tasks.InstanceServerUploader;
import org.odk.collect.android.utilities.IconUtils;
import org.odk.collect.android.utilities.InstanceUploaderUtils;
import org.odk.collect.android.utilities.PermissionUtils;
import org.odk.collect.android.utilities.gdrive.GoogleAccountsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.work.Worker;
import timber.log.Timber;

import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.AUTO_SEND;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes.FORMS_UPLOADED_NOTIFICATION;

public class AutoSendWorker extends Worker implements InstanceUploaderListener {
    public static final String TAG = "autoSendWorker";

    private InstanceServerUploader instanceServerUploader;
    private InstanceGoogleSheetsUploader instanceGoogleSheetsUploader;

    private String resultMessage;
    private CountDownLatch countDownLatch;
    Result workResult;

    @NonNull
    @Override
    public Result doWork() {        // make sure sd card is ready, if not don't try to send
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return Result.FAILURE;
        }

        countDownLatch = new CountDownLatch(1);

        ConnectivityManager manager = (ConnectivityManager) getApplicationContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetworkInfo = manager.getActiveNetworkInfo();

        uploadForms(getApplicationContext(), isFormAutoSendOptionEnabled(currentNetworkInfo));

        try {
            countDownLatch.await(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Timber.e(e);
        }
        return workResult;
    }

    private boolean isFormAutoSendOptionEnabled(NetworkInfo currentNetworkInfo) {
        // make sure autosend is enabled on the given connected interface
        String autosend = (String) GeneralSharedPreferences.getInstance().get(PreferenceKeys.KEY_AUTOSEND);
        boolean sendwifi = autosend.equals("wifi_only");
        boolean sendnetwork = autosend.equals("cellular_only");
        if (autosend.equals("wifi_and_cellular")) {
            sendwifi = true;
            sendnetwork = true;
        }

        return currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
                && sendwifi || currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE
                && sendnetwork;
    }

    /**
     * @param isFormAutoSendOptionEnabled represents whether the auto-send option is enabled at the app level
     */
    private void uploadForms(Context context, boolean isFormAutoSendOptionEnabled) {

        ArrayList<Long> toUpload = new ArrayList<>();
        Cursor c = new InstancesDao().getFinalizedInstancesCursor();

        try {
            if (c != null && c.getCount() > 0) {
                c.move(-1);
                String formId;
                while (c.moveToNext()) {
                    formId = c.getString(c.getColumnIndex(InstanceColumns.JR_FORM_ID));
                    if (isFormAutoSendEnabled(formId, isFormAutoSendOptionEnabled)) {
                        Long l = c.getLong(c.getColumnIndex(InstanceColumns._ID));
                        toUpload.add(l);
                    }
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (toUpload.isEmpty()) {
            return;
        }

        Long[] toSendArray = new Long[toUpload.size()];
        toUpload.toArray(toSendArray);

        GeneralSharedPreferences settings = GeneralSharedPreferences.getInstance();
        String protocol = (String) settings.get(PreferenceKeys.KEY_PROTOCOL);

        if (protocol.equals(context.getString(R.string.protocol_google_sheets))) {
            if (PermissionUtils.checkIfGetAccountsPermissionGranted(context)) {
                String googleUsername = (String) settings.get(PreferenceKeys.KEY_SELECTED_GOOGLE_ACCOUNT);
                if (googleUsername == null || googleUsername.isEmpty()) {

                    return;
                }
                GoogleAccountsManager accountsManager = new GoogleAccountsManager(Collect.getInstance());
                accountsManager.getCredential().setSelectedAccountName(googleUsername);
                instanceGoogleSheetsUploader = new InstanceGoogleSheetsUploader(accountsManager);
                instanceGoogleSheetsUploader.setUploaderListener(this);
                instanceGoogleSheetsUploader.execute(toSendArray);
            } else {
                resultMessage = Collect.getInstance().getString(R.string.odk_permissions_fail);
                uploadingComplete(null);
            }
        } else if (protocol.equals(context.getString(R.string.protocol_odk_default))) {

            instanceServerUploader = new InstanceServerUploader();
            instanceServerUploader.setUploaderListener(this);

            instanceServerUploader.execute(toSendArray);
        }
    }

    /**
     * @param isFormAutoSendOptionEnabled represents whether the auto-send option is enabled at the app level
     *                                    <p>
     *                                    If the form explicitly sets the auto-send property, then it overrides the preferences.
     */
    private boolean isFormAutoSendEnabled(String jrFormId, boolean isFormAutoSendOptionEnabled) {
        Cursor cursor = new FormsDao().getFormsCursorForFormId(jrFormId);
        String autoSend = null;
        if (cursor != null && cursor.moveToFirst()) {
            try {
                int autoSendColumnIndex = cursor.getColumnIndex(AUTO_SEND);
                autoSend = cursor.getString(autoSendColumnIndex);
            } finally {
                cursor.close();
            }
        }
        return autoSend == null ? isFormAutoSendOptionEnabled : Boolean.valueOf(autoSend);
    }

    @Override
    public void uploadingComplete(HashMap<String, String> result) {
        // task is done
        if (instanceServerUploader != null) {
            instanceServerUploader.setUploaderListener(null);
        }
        if (instanceGoogleSheetsUploader != null) {
            instanceGoogleSheetsUploader.setUploaderListener(null);
        }

        String message;

        if (result == null) {
            workResult = Result.FAILURE;

            message = resultMessage != null
                    ? resultMessage
                    : Collect.getInstance().getString(R.string.odk_auth_auth_fail);
        } else {
            workResult = Result.SUCCESS;

            StringBuilder selection = new StringBuilder();
            Set<String> keys = result.keySet();
            Iterator<String> it = keys.iterator();

            String[] selectionArgs = new String[keys.size()];
            int i = 0;
            while (it.hasNext()) {
                String id = it.next();
                selection.append(InstanceColumns._ID + "=?");
                selectionArgs[i++] = id;
                if (i != keys.size()) {
                    selection.append(" or ");
                }
            }

            message = InstanceUploaderUtils
                    .getUploadResultMessage(new InstancesDao().getInstancesCursor(selection.toString(), selectionArgs), result);
        }

        Intent notifyIntent = new Intent(Collect.getInstance(), NotificationActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifyIntent.putExtra(NotificationActivity.NOTIFICATION_TITLE, Collect.getInstance().getString(R.string.upload_results));
        notifyIntent.putExtra(NotificationActivity.NOTIFICATION_MESSAGE, message.trim());

        PendingIntent pendingNotify = PendingIntent.getActivity(Collect.getInstance(), FORMS_UPLOADED_NOTIFICATION,
                notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(Collect.getInstance())
                .setSmallIcon(IconUtils.getNotificationAppIcon())
                .setContentTitle(Collect.getInstance().getString(R.string.odk_auto_note))
                .setContentIntent(pendingNotify)
                .setContentText(getContentText(result))
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) Collect.getInstance()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1328974928, builder.build());

        countDownLatch.countDown();
    }

    private String getContentText(Map<String, String> result) {
        return result != null && allFormsDownloadedSuccessfully(result)
                ? Collect.getInstance().getString(R.string.success)
                : Collect.getInstance().getString(R.string.failures);
    }

    private boolean allFormsDownloadedSuccessfully(Map<String, String> result) {
        for (Map.Entry<String, String> item : result.entrySet()) {
            if (!item.getValue().equals(InstanceUploaderUtils.DEFAULT_SUCCESSFUL_TEXT)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void progressUpdate(int progress, int total) {
        // do nothing
    }

    @Override
    public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
        // if we get an auth request, just fail
        if (instanceServerUploader != null) {
            instanceServerUploader.setUploaderListener(null);
        }
        if (instanceGoogleSheetsUploader != null) {
            instanceGoogleSheetsUploader.setUploaderListener(null);
        }

        workResult = Result.FAILURE;
        countDownLatch.countDown();
    }
}