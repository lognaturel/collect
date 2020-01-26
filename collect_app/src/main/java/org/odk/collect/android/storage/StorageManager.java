package org.odk.collect.android.storage;

import android.os.Environment;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.preferences.GeneralSharedPreferences;

import java.io.File;

import timber.log.Timber;

import static org.odk.collect.android.preferences.GeneralKeys.KEY_SCOPED_STORAGE_USED;

public class StorageManager {
    public String[] getRequiredDirPaths() {
        return new String[]{
                getMainODKDirPath(),
                getAbsolutePath(Subdirectory.FORMS),
                getAbsolutePath(Subdirectory.INSTANCES),
                getAbsolutePath(Subdirectory.CACHE),
                getAbsolutePath(Subdirectory.METADATA),
                getAbsolutePath(Subdirectory.LAYERS)
            };
    }

    private String getStoragePath() {
        return isScopedStorageUsed()
                ? getScopedExternalFilesDir()
                : getRootExternalFilesDir();
    }

    String getScopedExternalFilesDir() {
        File primaryStorageFile = Collect.getInstance().getExternalFilesDir(null);
        if (primaryStorageFile != null) {
            return primaryStorageFile.getAbsolutePath();
        }
        return "";
    }

    String getRootExternalFilesDir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public String getMainODKDirPath() {
        return getStoragePath() + File.separator + "odk";
    }

    public String getAbsolutePath(Subdirectory subdirectory) {
        return getMainODKDirPath() + File.separator + subdirectory.getDirectoryName();
    }

    public String getAbsolutePath(Subdirectory subdirectory, String filePath) {
        if (filePath == null) {
            return null;
        }
        return filePath.startsWith(getAbsolutePath(subdirectory))
                ? filePath
                : getAbsolutePath(subdirectory) + File.separator + filePath;
    }

    boolean isScopedStorageUsed() {
        return GeneralSharedPreferences.getInstance().getBoolean(KEY_SCOPED_STORAGE_USED, false);
    }

    public void recordMigrationToScopedStorage() {
        GeneralSharedPreferences.getInstance().save(KEY_SCOPED_STORAGE_USED, true);
    }

    // TODO the method should be removed once using Scoped storage became required
    public String getDbPathFromRelativePath(Subdirectory subdirectory, String relativePath) {
        return isScopedStorageUsed()
                ? relativePath
                : getAbsolutePath(subdirectory) + File.separator + relativePath;
    }

    // TODO the method should be removed once using Scoped storage became required
    public String getRelativePath(Subdirectory subdirectory, String filePath) {
        return filePath.startsWith(getAbsolutePath(subdirectory))
                ? filePath.substring(getAbsolutePath(subdirectory).length() + 1)
                : filePath;
    }

    public String getTmpFilePath() {
        return getAbsolutePath(Subdirectory.CACHE) + File.separator + "tmp.jpg";
    }

    public String getTmpDrawFilePath() {
        return getAbsolutePath(Subdirectory.CACHE) + File.separator + "tmpDraw.jpg";
    }

    /**
     * Creates required directories on the SDCard (or other external storage)
     *
     * @throws RuntimeException if there is no SDCard or the directory exists as a non directory
     */
    public void createODKDirs() throws RuntimeException {
        if (!isStorageAvailable()) {
            throw new RuntimeException(
                    Collect.getInstance().getString(R.string.sdcard_unmounted, getStorageState()));
        }

        for (String dirPath : getRequiredDirPaths()) {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    String message = Collect.getInstance().getString(R.string.cannot_create_directory, dirPath);
                    Timber.w(message);
                    throw new RuntimeException(message);
                }
            } else {
                if (!dir.isDirectory()) {
                    String message = Collect.getInstance().getString(R.string.not_a_directory, dirPath);
                    Timber.w(message);
                    throw new RuntimeException(message);
                }
            }
        }
    }

    private boolean isStorageAvailable() {
        return getStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    private String getStorageState() {
        return Environment.getExternalStorageState();
    }

    public enum Subdirectory {
        FORMS("forms"),
        INSTANCES("instances"),
        CACHE(".cache"),
        METADATA("metadata"),
        LAYERS("layers"),
        SETTINGS("settings");

        private String directoryName;

        Subdirectory(String directoryName) {
            this.directoryName = directoryName;
        }

        public String getDirectoryName() {
            return directoryName;
        }
    }
}
