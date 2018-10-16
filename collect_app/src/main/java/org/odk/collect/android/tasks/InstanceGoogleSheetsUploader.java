/*
 * Copyright (C) 2018 Nafundi
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

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.auth.GoogleAuthException;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.dto.Form;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.http.CollectServerClient.Outcome;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.gdrive.GoogleAccountsManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

import static org.odk.collect.android.utilities.InstanceUploaderUtils.DEFAULT_SUCCESSFUL_TEXT;

public class InstanceGoogleSheetsUploader extends InstanceUploader {
    private boolean authFailed;

    private InstanceGoogleSheetsUploaderFriend uploader;

    public InstanceGoogleSheetsUploader(GoogleAccountsManager accountsManager) {
        uploader = new InstanceGoogleSheetsUploaderFriend(accountsManager);
    }

    @Override
    protected Outcome doInBackground(Long... instanceIdsToUpload) {
        final Outcome outcome = new Outcome();

        try {
            // User-recoverable auth error
            if (uploader.getAuthToken() == null) {
                return null;
            }
        } catch (IOException | GoogleAuthException e) {
            Timber.e(e);
            authFailed = true;
        }

        if (!uploader.submissionsFolderExistsAndIsUnique()) {
            return outcome;
        }

        List<Instance> instancesToUpload = getInstancesFromIds(instanceIdsToUpload);

        for (int i = 0; i < instancesToUpload.size(); i++) {
            if (isCancelled()) {
                return outcome;
            }

            Instance instance = instancesToUpload.get(i);

            publishProgress(i + 1, instancesToUpload.size());

            String urlString = uploader.getUrlToSubmitTo(instance);

            // Get corresponding blank form and verify there is exactly 1
            FormsDao dao = new FormsDao();
            Cursor formCursor = dao.getFormsCursor(instance.getJrFormId(), instance.getJrVersion());
            List<Form> forms = dao.getFormsFromCursor(formCursor);

            if (forms.size() != 1) {
                outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(),
                        Collect.getInstance().getString(R.string.not_exactly_one_blank_form_for_this_form_id));
            } else {
                Form form = forms.get(0);
                Uri instanceDatabaseUri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI,
                        instance.getDatabaseId().toString());

                try {
                    uploader.uploadOneSubmission(instance, new File(instance.getInstanceFilePath()),
                            form.getFormFilePath(), urlString);

                    uploader.saveSuccessStatusToDatabase(instanceDatabaseUri);

                    outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(), DEFAULT_SUCCESSFUL_TEXT);

                    Collect.getInstance()
                            .getDefaultTracker()
                            .send(new HitBuilders.EventBuilder()
                                    .setCategory("Submission")
                                    .setAction("HTTP-Sheets")
                                    .build());
                } catch (InstanceGoogleSheetsUploaderFriend.UploadException e) {
                    Timber.e(e);
                    outcome.messagesByInstanceId.put(instance.getDatabaseId().toString(),
                            e.getMessage() != null ? e.getMessage() : e.getCause().getMessage());

                    uploader.saveFailedStatusToDatabase(instanceDatabaseUri);
                }
            }
        }
        return outcome;
    }

    public boolean isAuthFailed() {
        return authFailed;
    }

    public void setAuthFailedToFalse() {
        authFailed = false;
    }
}