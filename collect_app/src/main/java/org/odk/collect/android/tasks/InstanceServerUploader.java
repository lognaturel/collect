/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.http.CollectServerClient.Outcome;
import org.odk.collect.android.http.OpenRosaHttpInterface;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.WebCredentialsUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Background task for uploading completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceServerUploader extends InstanceUploader {
    @Inject
    OpenRosaHttpInterface httpInterface;

    @Inject
    WebCredentialsUtils webCredentialsUtils;

    // Custom submission URL, username and password that can be sent via intent extras by external
    // applications
    private String completeDestinationUrl;
    private String customUsername;
    private String customPassword;

    private InstanceServerUploaderFriend friend;

    public InstanceServerUploader() {
        Collect.getInstance().getComponent().inject(this);

        friend = new InstanceServerUploaderFriend(httpInterface, webCredentialsUtils);
    }

    @Override
    protected Outcome doInBackground(Long... instanceIdsToUpload) {
        Outcome outcome = new Outcome();

        List<Instance> instancesToUpload = getInstancesFromIds(instanceIdsToUpload);

        String deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                    .getSingularProperty(PropertyManager.withUri(PropertyManager.PROPMGR_DEVICE_ID));

        Map<Uri, Uri> uriRemap = new HashMap<>();

        for (int i = 0; i < instancesToUpload.size(); i++) {
            if (isCancelled()) {
                return outcome;
            }
            Instance instance = instancesToUpload.get(i);

            publishProgress(i + 1, instancesToUpload.size());

            String urlString = getUrlToSubmitTo(instance, deviceId);

            InstanceServerUploaderFriend.UploadResult result = friend.uploadOneSubmission(instance,
                    urlString, uriRemap);

            if (result.getAuthRequestingServerUri() != null) {
                outcome.authRequestingServer = result.getAuthRequestingServerUri();
            } else {
                // Don't add the instance that caused an auth request to the map because that would
                // mark it as completed and we want to retry
                outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), result.getDisplayMessage());
            }

            if (result.isFatalError()) {
                return outcome;
            }
        }
        
        return outcome;
    }

    /**
     * Returns the URL this instance should be submitted to with appended deviceId.
     *
     * If the upload was triggered by an external app and specified a custom URL, use that one.
     * Otherwise, use the submission URL configured in the form
     * (https://opendatakit.github.io/xforms-spec/#submission-attributes). Finally, default to the
     * URL configured at the submission level.
     */
    @NonNull
    private String getUrlToSubmitTo(Instance currentInstance, String deviceId) {
        String urlString;

        if (completeDestinationUrl != null) {
            urlString = completeDestinationUrl;
        } else if (currentInstance.getSubmissionUri() != null) {
            urlString = currentInstance.getSubmissionUri().trim();
        } else {
            urlString = friend.getServerSubmissionURL();
        }

        // add deviceID to request
        try {
            urlString += "?deviceID=" + URLEncoder.encode(deviceId != null ? deviceId : "", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Timber.i(e, "Error encoding URL for device id : %s", deviceId);
        }

        return urlString;
    }

    @Override
    protected void onPostExecute(Outcome outcome) {
        super.onPostExecute(outcome);

        // Clear temp credentials
        clearTemporaryCredentials();
    }

    @Override
    protected void onCancelled() {
        clearTemporaryCredentials();
    }

    public void setCompleteDestinationUrl(String completeDestinationUrl) {
        setCompleteDestinationUrl(completeDestinationUrl, true);
    }

    public void setCompleteDestinationUrl(String completeDestinationUrl, boolean clearPreviousConfig) {
        this.completeDestinationUrl = completeDestinationUrl;
        if (clearPreviousConfig) {
            setTemporaryCredentials();
        }
    }

    public void setCustomUsername(String customUsername) {
        this.customUsername = customUsername;
        setTemporaryCredentials();
    }

    public void setCustomPassword(String customPassword) {
        this.customPassword = customPassword;
        setTemporaryCredentials();
    }

    private void setTemporaryCredentials() {
        if (customUsername != null && customPassword != null) {
            webCredentialsUtils.saveCredentials(completeDestinationUrl, customUsername, customPassword);
        } else {
            // In the case for anonymous logins, clear the previous credentials for that host
            webCredentialsUtils.clearCredentials(completeDestinationUrl);
        }
    }

    private void clearTemporaryCredentials() {
        if (customUsername != null && customPassword != null) {
            webCredentialsUtils.clearCredentials(completeDestinationUrl);
        }
    }
}
