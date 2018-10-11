package org.odk.collect.android.tasks;

import android.content.ContentValues;
import android.net.Uri;

import com.google.android.gms.analytics.HitBuilders;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.http.CollectServerClient;
import org.odk.collect.android.http.HttpHeadResult;
import org.odk.collect.android.http.OpenRosaHttpInterface;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.ResponseMessageParser;
import org.odk.collect.android.utilities.WebCredentialsUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import timber.log.Timber;

public class InstanceServerUploaderFriend {
    private static final String FAIL = "Error: ";

    OpenRosaHttpInterface httpInterface;
    WebCredentialsUtils webCredentialsUtils;

    public InstanceServerUploaderFriend(OpenRosaHttpInterface httpInterface, WebCredentialsUtils webCredentialsUtils) {
        this.httpInterface = httpInterface;
        this.webCredentialsUtils = webCredentialsUtils;
    }

    /**
     * Uploads all files associated with an instance to the specified URL.
     *
     * @return false if credentials are required and we should terminate immediately.
     */
    public boolean uploadOneSubmission(Instance instance, String urlString, Map<Uri, Uri> uriRemap,
                                        CollectServerClient.Outcome outcome) {
        Uri instanceDatabaseUri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI,
                instance.getDatabaseId().toString());

        ContentValues contentValues = new ContentValues();
        Uri submissionUri = Uri.parse(urlString);

        boolean openRosaServer = false;
        if (uriRemap.containsKey(submissionUri)) {
            // we already issued a head request and got a response,
            // so we know the proper URL to send the submission to
            // and the proper scheme. We also know that it was an
            // OpenRosa compliant server.
            openRosaServer = true;
            submissionUri = uriRemap.get(submissionUri);
            Timber.i("Using Uri remap for submission %s. Now: %s", instance.getDatabaseId(),
                    submissionUri.toString());
        } else {
            if (submissionUri.getHost() == null) {
                Timber.i("Host name may not be null");
                outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(),
                        FAIL + "Host name may not be null");
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return true;
            }

            URI uri;
            try {
                uri = URI.create(submissionUri.toString());
            } catch (IllegalArgumentException e) {
                Timber.i(e);
                outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), Collect.getInstance().getString(R.string.url_error));
                return false;
            }

            try {
                HttpHeadResult headResult = httpInterface.head(uri, webCredentialsUtils.getCredentials(uri));
                Map<String, String> responseHeaders = headResult.getHeaders();

                if (headResult.getStatusCode() == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                    outcome.authRequestingServer = submissionUri;
                    return false;
                } else if (headResult.getStatusCode() == HttpsURLConnection.HTTP_NO_CONTENT) {
                    if (responseHeaders.containsKey("Location")) {
                        try {
                            Uri newURI = Uri.parse(URLDecoder.decode(responseHeaders.get("Location"), "utf-8"));
                            if (submissionUri.getHost().equalsIgnoreCase(newURI.getHost())) {
                                openRosaServer = true;
                                // trust the server to tell us a new location
                                // ... and possibly to use https instead.
                                // Re-add params if server didn't respond with params
                                if (newURI.getQuery() == null) {
                                    newURI = newURI.buildUpon()
                                            .encodedQuery(submissionUri.getEncodedQuery())
                                            .build();
                                }
                                uriRemap.put(submissionUri, newURI);
                                submissionUri = newURI;
                            } else {
                                // Don't follow a redirection attempt to a different host.
                                // We can't tell if this is a spoof or not.
                                outcome.messagesByInstanceId.put(
                                        instance.getDatabaseId().toString(),
                                        FAIL
                                                + "Unexpected redirection attempt to a different "
                                                + "host: "
                                                + newURI.toString());
                                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                        InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                                Collect.getInstance().getContentResolver()
                                        .update(instanceDatabaseUri, contentValues, null, null);
                                return true;
                            }
                        } catch (Exception e) {
                            Timber.e(e, "Exception thrown parsing URI for url %s", urlString);
                            outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(),
                                    FAIL + urlString + " " + e.toString());
                            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                    InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                            Collect.getInstance().getContentResolver()
                                    .update(instanceDatabaseUri, contentValues, null, null);
                            return true;
                        }
                    }

                } else {
                    Timber.w("Status code on Head request: %d", headResult.getStatusCode());
                    if (headResult.getStatusCode() >= HttpsURLConnection.HTTP_OK && headResult.getStatusCode() < HttpsURLConnection.HTTP_MULT_CHOICE) {
                        outcome.messagesByInstanceId.put(
                                instance.getDatabaseId().toString(),
                                FAIL
                                        + "Invalid status code on Head request.  If you have a "
                                        + "web proxy, you may need to login to your network. ");
                        contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                        Collect.getInstance().getContentResolver()
                                .update(instanceDatabaseUri, contentValues, null, null);
                        return true;
                    }
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }

                outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + msg);
                Timber.e(e);
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return true;
            }
        }

        // At this point, we may have updated the uri to use https.
        // This occurs only if the Location header keeps the host name
        // the same. If it specifies a different host name, we error
        // out.
        //
        // And we may have set authentication cookies in our
        // cookiestore (referenced by localContext) that will enable
        // authenticated publication to the server.
        //
        // get instance file

        // Under normal operations, we upload the instanceFile to
        // the server.  However, during the save, there is a failure
        // window that may mark the submission as complete but leave
        // the file-to-be-uploaded with the name "submission.xml" and
        // the plaintext submission files on disk.  In this case,
        // upload the submission.xml and all the files in the directory.
        // This means the plaintext files and the encrypted files
        // will be sent to the server and the server will have to
        // figure out what to do with them.

        File instanceFile = new File(instance.getInstanceFilePath());
        File submissionFile = new File(instanceFile.getParentFile(), "submission.xml");
        if (submissionFile.exists()) {
            Timber.w("submission.xml will be uploaded instead of %s", instanceFile.getAbsolutePath());
        } else {
            submissionFile = instanceFile;
        }

        if (!instanceFile.exists() && !submissionFile.exists()) {
            outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + "instance XML file does not exist!");
            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
            return true;
        }

        List<File> files = getFilesInParentDirectory(instanceFile, submissionFile, openRosaServer);

        if (files == null) {
            return false;
        }

        ResponseMessageParser messageParser;

        try {
            URI uri = URI.create(submissionUri.toString());

            messageParser = httpInterface.uploadSubmissionFile(files, submissionFile, uri,
                    webCredentialsUtils.getCredentials(uri));

            int responseCode = messageParser.getResponseCode();

            if (responseCode != HttpsURLConnection.HTTP_CREATED && responseCode != HttpsURLConnection.HTTP_ACCEPTED) {
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + "Network login failure? Again?");
                } else if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                    outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + messageParser.getReasonPhrase() + " (" + responseCode + ") at " + urlString);
                } else {
                    // If response from server is valid use that else use default messaging
                    if (messageParser.isValid()) {
                        outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + messageParser.getMessageResponse());
                    } else {
                        outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + messageParser.getReasonPhrase() + " (" + responseCode + ") at " + urlString);
                    }

                }
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return true;
            }

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.toString();
            }
            outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), FAIL + "Generic Exception: " + msg);
            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
            return true;
        }

        // If response from server is valid use that else use default messaging
        if (messageParser.isValid()) {
            outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), messageParser.getMessageResponse());
        } else {
            // Default messaging
            outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), Collect.getInstance().getString(R.string.success));
        }

        contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
        Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);

        Collect.getInstance()
                .getDefaultTracker()
                .send(new HitBuilders.EventBuilder()
                        .setCategory("Submission")
                        .setAction("HTTP")
                        .build());

        return true;
    }

    private List<File> getFilesInParentDirectory(File instanceFile, File submissionFile, boolean openRosaServer) {
        List<File> files = new ArrayList<>();

        // find all files in parent directory
        File[] allFiles = instanceFile.getParentFile().listFiles();
        if (allFiles == null) {
            return null;
        }

        for (File f : allFiles) {
            String fileName = f.getName();

            if (fileName.startsWith(".")) {
                continue; // ignore invisible files
            } else if (fileName.equals(instanceFile.getName())) {
                continue; // the xml file has already been added
            } else if (fileName.equals(submissionFile.getName())) {
                continue; // the xml file has already been added
            }

            String extension = FileUtils.getFileExtension(fileName);

            if (openRosaServer) {
                files.add(f);
            } else if (extension.equals("jpg")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gpp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("mp4")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("osm")) { // legacy 0.9x
                files.add(f);
            } else {
                Timber.w("unrecognized file type %s", f.getName());
            }
        }

        return files;
    }
}
