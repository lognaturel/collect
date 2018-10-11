package org.odk.collect.android.tasks;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.analytics.HitBuilders;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.http.HttpHeadResult;
import org.odk.collect.android.http.OpenRosaHttpInterface;
import org.odk.collect.android.preferences.PreferenceKeys;
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
    private static final String URL_PATH_SEP = "/";
    private static final String FAIL = "Error: ";

    private OpenRosaHttpInterface httpInterface;
    private WebCredentialsUtils webCredentialsUtils;

    public InstanceServerUploaderFriend() {

    }

    public InstanceServerUploaderFriend(OpenRosaHttpInterface httpInterface, WebCredentialsUtils webCredentialsUtils) {
        this.httpInterface = httpInterface;
        this.webCredentialsUtils = webCredentialsUtils;
    }

    public class UploadResult {
        @NonNull
        private UploadResultTypes type;
        @Nullable
        private String customMessage;

        // The server requesting auth may be different than the server a request was made to because
        // of a redirect. The server requesting auth should be shown to the user so that the user
        // can decide whether to trust the server and what credentials to provide.
        @Nullable
        private Uri authRequestingServerUri;

        public UploadResult(@NonNull UploadResultTypes type, @Nullable String customMessage,
                            @Nullable Uri authRequestingServerUri) {
            this.type = type;
            this.customMessage = customMessage;
            this.authRequestingServerUri = authRequestingServerUri;
        }

        public UploadResult(@NonNull UploadResultTypes type, @Nullable String customMessage) {
            this(type, customMessage, null);
        }

        public UploadResult(@NonNull UploadResultTypes type, @Nullable Uri authRequestingServerUri) {
            this(type, null, authRequestingServerUri);
        }

        public UploadResult(@NonNull UploadResultTypes type) {
            this(type, null, null);
        }

        /**
         * Returns a message to display to the user. A custom message takes precedence, followed by
         * a localized message, followed by a generic default message.
         */
        @NonNull
        public String getDisplayMessage() {
            if (customMessage != null) {
                return customMessage;
            } else if (type.localizedMessageId != null) {
                return Collect.getInstance().getString(type.getLocalizedMessageId());
            } else {
                return type.getFallbackMessage();
            }
        }

        public boolean isFatalError() {
            return type.isFatalError();
        }

        @Nullable
        public Uri getAuthRequestingServerUri() {
            return authRequestingServerUri;
        }
    }

    public enum UploadResultTypes {
        HOST_NAME_NULL(null, FAIL + "Host name may not be null", false),
        URL_ERROR(R.string.url_error, "", true),
        AUTH_REQUESTED(null, "Authorization requested", true),
        UNEXPECTED_REDIRECT(null, FAIL + "Unexpected redirection attempt to a different host", false),
        URI_PARSE_ERROR(null, "Exception thrown parsing URI", false),
        INVALID_HEAD_STATUS(null, FAIL + "Invalid status code on HEAD request.  If you have a "
                + "web proxy, you may need to login to your network. ", false),
        HEAD_REQUEST_EXCEPTION(null, FAIL + "Exception performing HEAD request.", false),
        SUBMISSION_XML_INEXISTENT(null, FAIL + "instance XML file does not exist!", false),
        // TODO: why would this be fatalError?
        NO_FILES_IN_PARENT_DIR(null, FAIL + "no files in parent directory", true),
        HTTP_NOT_ACCEPTED_OR_CREATED(null, FAIL + "HTTP response code not expected", false),
        GENERIC_EXCEPTION(null, FAIL + "Generic exception", false),

        SUCCESS(R.string.success, "Success", false);

        private final Integer localizedMessageId;
        @NonNull
        private final String fallbackMessage;
        private final boolean fatalError;

        UploadResultTypes(Integer localizedMessageId, @NonNull String fallbackMessage, boolean fatalError) {
            this.fallbackMessage = fallbackMessage;
            this.localizedMessageId = localizedMessageId;
            this.fatalError = fatalError;
        }

        public Integer getLocalizedMessageId() {
            return localizedMessageId;
        }

        @NonNull
        public String getFallbackMessage() {
            return fallbackMessage;
        }

        public boolean isFatalError() {
            return fatalError;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }

    /**
     * Uploads all files associated with an instance to the specified URL.
     *
     * @return false if credentials are required and we should terminate immediately.
     */
    public UploadResult uploadOneSubmission(Instance instance, String urlString, Map<Uri, Uri> uriRemap) {
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
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return new UploadResult(UploadResultTypes.HOST_NAME_NULL);
            }

            URI uri;
            try {
                uri = URI.create(submissionUri.toString());
            } catch (IllegalArgumentException e) {
                Timber.i(e);
                return new UploadResult(UploadResultTypes.URL_ERROR);
            }

            try {
                HttpHeadResult headResult = httpInterface.head(uri, webCredentialsUtils.getCredentials(uri));
                Map<String, String> responseHeaders = headResult.getHeaders();

                if (headResult.getStatusCode() == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                    return new UploadResult(UploadResultTypes.AUTH_REQUESTED, submissionUri);
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
                                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                        InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                                Collect.getInstance().getContentResolver()
                                        .update(instanceDatabaseUri, contentValues, null, null);
                                return new UploadResult(UploadResultTypes.UNEXPECTED_REDIRECT,
                                        FAIL + "Unexpected redirection attempt to a "
                                                + "different host: " + newURI.toString());
                            }
                        } catch (Exception e) {
                            Timber.i(e, "Exception thrown parsing URI for url %s", urlString);
                            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                    InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                            Collect.getInstance().getContentResolver()
                                    .update(instanceDatabaseUri, contentValues, null, null);
                            return new UploadResult(UploadResultTypes.URI_PARSE_ERROR,
                                    FAIL + urlString + " " + e.toString());
                        }
                    }

                } else {
                    Timber.w("Status code on Head request: %d", headResult.getStatusCode());
                    if (headResult.getStatusCode() >= HttpsURLConnection.HTTP_OK && headResult.getStatusCode() < HttpsURLConnection.HTTP_MULT_CHOICE) {
                        contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS,
                                InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                        Collect.getInstance().getContentResolver()
                                .update(instanceDatabaseUri, contentValues, null, null);
                        return new UploadResult(UploadResultTypes.INVALID_HEAD_STATUS);
                    }
                }
            } catch (Exception e) {
                Timber.e(e);
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return new UploadResult(UploadResultTypes.HEAD_REQUEST_EXCEPTION,
                        e.getMessage() != null ? e.getMessage() : e.toString());
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
            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
            return new UploadResult(UploadResultTypes.SUBMISSION_XML_INEXISTENT);
        }

        List<File> files = getFilesInParentDirectory(instanceFile, submissionFile, openRosaServer);

        // TODO: When can this case happen? Why is it fatalError?
        if (files == null) {
            return new UploadResult(UploadResultTypes.NO_FILES_IN_PARENT_DIR);
        }

        ResponseMessageParser messageParser;

        try {
            URI uri = URI.create(submissionUri.toString());

            messageParser = httpInterface.uploadSubmissionFile(files, submissionFile, uri,
                    webCredentialsUtils.getCredentials(uri));

            int responseCode = messageParser.getResponseCode();

            if (responseCode != HttpsURLConnection.HTTP_CREATED && responseCode != HttpsURLConnection.HTTP_ACCEPTED) {
                UploadResult result;

                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    result = new UploadResult(UploadResultTypes.HTTP_NOT_ACCEPTED_OR_CREATED,
                            FAIL + "Network login failure? Again?");
                } else if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                    result = new UploadResult(UploadResultTypes.HTTP_NOT_ACCEPTED_OR_CREATED,
                            FAIL + messageParser.getReasonPhrase() + " (" + responseCode +
                                    ") at " + urlString);
                } else {
                    // If response from server is valid use that else use default messaging
                    if (messageParser.isValid()) {
                        result = new UploadResult(UploadResultTypes.HTTP_NOT_ACCEPTED_OR_CREATED,
                                FAIL + messageParser.getMessageResponse());
                    } else {
                        result = new UploadResult(UploadResultTypes.HTTP_NOT_ACCEPTED_OR_CREATED,
                                FAIL + messageParser.getReasonPhrase() + " (" + responseCode +
                                        ") at " + urlString);
                    }

                }
                contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
                return result;
            }

        } catch (IOException e) {
            contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);
            return new UploadResult(UploadResultTypes.GENERIC_EXCEPTION,
                    "Generic Exception: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }

        contentValues.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
        Collect.getInstance().getContentResolver().update(instanceDatabaseUri, contentValues, null, null);

        Collect.getInstance()
                .getDefaultTracker()
                .send(new HitBuilders.EventBuilder()
                        .setCategory("Submission")
                        .setAction("HTTP")
                        .build());

        return new UploadResult(UploadResultTypes.SUCCESS,
                // Use response from server if valid
                messageParser.isValid() ? messageParser.getMessageResponse() : null);
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

    public String getServerSubmissionURL() {
        Collect app = Collect.getInstance();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(app);
        String serverBase = settings.getString(PreferenceKeys.KEY_SERVER_URL,
                app.getString(R.string.default_server_url));

        if (serverBase.endsWith(URL_PATH_SEP)) {
            serverBase = serverBase.substring(0, serverBase.length() - 1);
        }

        // NOTE: /submission must not be translated! It is the well-known path on the server.
        String submissionPath = settings.getString(PreferenceKeys.KEY_SUBMISSION_URL,
                app.getString(R.string.default_odk_submission));

        if (!submissionPath.startsWith(URL_PATH_SEP)) {
            submissionPath = URL_PATH_SEP + submissionPath;
        }

        return serverBase + submissionPath;
    }
}
