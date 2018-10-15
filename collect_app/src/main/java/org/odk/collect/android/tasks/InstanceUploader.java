/*
 * Copyright 2016 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.tasks;

import android.database.Cursor;
import android.database.SQLException;
import android.os.AsyncTask;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.dto.Instance;
import org.odk.collect.android.http.CollectServerClient;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.ApplicationConstants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.AUTO_DELETE;

public abstract class InstanceUploader extends AsyncTask<Long, Integer, CollectServerClient.Outcome> {

    private InstanceUploaderListener stateListener;
    private Boolean deleteInstanceAfterSubmission;

    @Override
    protected void onPostExecute(CollectServerClient.Outcome outcome) {
        synchronized (this) {
            if (outcome != null && stateListener != null) {
                if (outcome.authRequestingServer != null) {
                    stateListener.authRequest(outcome.authRequestingServer, outcome.messagesByInstanceId);
                } else {
                    stateListener.uploadingComplete(outcome.messagesByInstanceId);

                    // Delete instances that were successfully sent and that need to be deleted
                    Set<String> keys = outcome.messagesByInstanceId.keySet();
                    Iterator<String> it = keys.iterator();
                    int count = keys.size();
                    while (count > 0) {
                        String[] selectionArgs;
                        if (count > ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER - 1) {
                            selectionArgs = new String[
                                    ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER];
                        } else {
                            selectionArgs = new String[count + 1];
                        }

                        StringBuilder selection = new StringBuilder();

                        selection.append(InstanceProviderAPI.InstanceColumns._ID + " IN (");
                        int i = 0;

                        while (it.hasNext() && i < selectionArgs.length - 1) {
                            selectionArgs[i] = it.next();
                            selection.append('?');

                            if (i != selectionArgs.length - 2) {
                                selection.append(',');
                            }
                            i++;
                        }

                        count -= selectionArgs.length - 1;
                        selection.append(") and status=?");
                        selectionArgs[i] = InstanceProviderAPI.STATUS_SUBMITTED;

                        Cursor results = null;
                        try {
                            results =
                                    new InstancesDao().getInstancesCursor(selection.toString(),
                                            selectionArgs);
                            if (results != null && results.getCount() > 0) {
                                List<Long> toDelete = new ArrayList<>();
                                results.moveToPosition(-1);

                                boolean isFormAutoDeleteOptionEnabled = (boolean) GeneralSharedPreferences.getInstance().get(PreferenceKeys.KEY_DELETE_AFTER_SEND);

                                // The custom configuration from the third party app overrides
                                // the app preferences set for delete after submission
                                if (deleteInstanceAfterSubmission != null) {
                                    isFormAutoDeleteOptionEnabled = deleteInstanceAfterSubmission;
                                }

                                String formId;
                                while (results.moveToNext()) {
                                    formId = results.getString(results.getColumnIndex(InstanceProviderAPI.InstanceColumns.JR_FORM_ID));
                                    if (isFormAutoDeleteEnabled(formId, isFormAutoDeleteOptionEnabled)) {
                                        toDelete.add(results.getLong(results.getColumnIndex(InstanceProviderAPI.InstanceColumns._ID)));
                                    }
                                }

                                DeleteInstancesTask dit = new DeleteInstancesTask();
                                dit.setContentResolver(Collect.getInstance().getContentResolver());
                                dit.execute(toDelete.toArray(new Long[toDelete.size()]));
                            }
                        } catch (SQLException e) {
                            Timber.e(e);
                        } finally {
                            if (results != null) {
                                results.close();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param isFormAutoDeleteOptionEnabled represents whether the auto-delete option is enabled at the app level
     *                                      <p>
     *                                      If the form explicitly sets the auto-delete property, then it overrides the preferences.
     */
    public static boolean isFormAutoDeleteEnabled(String jrFormId, boolean isFormAutoDeleteOptionEnabled) {
        Cursor cursor = new FormsDao().getFormsCursorForFormId(jrFormId);
        String autoDelete = null;
        if (cursor != null && cursor.moveToFirst()) {
            try {
                int autoDeleteColumnIndex = cursor.getColumnIndex(AUTO_DELETE);
                autoDelete = cursor.getString(autoDeleteColumnIndex);
            } finally {
                cursor.close();
            }
        }
        return autoDelete == null ? isFormAutoDeleteOptionEnabled : Boolean.valueOf(autoDelete);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (stateListener != null) {
                stateListener.progressUpdate(values[0], values[1]);
            }
        }
    }

    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            stateListener = sl;
        }
    }

    public void setDeleteInstanceAfterSubmission(Boolean deleteInstanceAfterSubmission) {
        this.deleteInstanceAfterSubmission = deleteInstanceAfterSubmission;
    }

    /**
     * Returns a list of Instance objects corresponding to the database IDs passed in.
     */
    protected List<Instance> getInstancesFromIds(Long... instanceDatabaseIds) {
        List<Instance> instancesToUpload = new ArrayList<>();
        InstancesDao dao = new InstancesDao();

        // Split the queries to avoid exceeding SQLITE_MAX_VARIABLE_NUMBER
        int counter = 0;
        while (counter * ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER < instanceDatabaseIds.length) {
            int low = counter * ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER;
            int high = (counter + 1) * ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER;
            if (high > instanceDatabaseIds.length) {
                high = instanceDatabaseIds.length;
            }

            StringBuilder selectionBuf = new StringBuilder(InstanceProviderAPI.InstanceColumns._ID + " IN (");
            String[] selectionArgs = new String[high - low];
            for (int i = 0; i < (high - low); i++) {
                if (i > 0) {
                    selectionBuf.append(',');
                }
                selectionBuf.append('?');
                selectionArgs[i] = instanceDatabaseIds[i + low].toString();
            }

            selectionBuf.append(')');
            String selection = selectionBuf.toString();

            Cursor c = dao.getInstancesCursor(selection, selectionArgs);
            instancesToUpload.addAll(dao.getInstancesFromCursor(c));

            counter++;
        }

        return instancesToUpload;
    }
}
