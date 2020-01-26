package org.odk.collect.android.storage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class StorageManagerTest {

    private final StorageManager storageManager = spy(StorageManager.class);

    @Before
    public void setup() {
        doReturn("/storage/emulated/0/Android/data/org.odk.collect.android/files").when(storageManager).getScopedExternalFilesDir();
        doReturn("/storage/emulated/0").when(storageManager).getRootExternalFilesDir();
    }

    private void mockUsingScopedStorage() {
        doReturn(true).when(storageManager).isScopedStorageUsed();
    }

    private void mockUsingSdCard() {
        doReturn(false).when(storageManager).isScopedStorageUsed();
    }

    @Test
    public void when_scopedStorageNotUsed_should_sdCardPathsBeUsed() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk", storageManager.getMainODKDirPath());
        assertEquals("/storage/emulated/0/odk/forms", storageManager.getAbsolutePath(StorageManager.Subdirectory.FORMS));
        assertEquals("/storage/emulated/0/odk/instances", storageManager.getAbsolutePath(StorageManager.Subdirectory.INSTANCES));
        assertEquals("/storage/emulated/0/odk/metadata", storageManager.getAbsolutePath(StorageManager.Subdirectory.METADATA));
        assertEquals("/storage/emulated/0/odk/.cache", storageManager.getAbsolutePath(StorageManager.Subdirectory.CACHE));
        assertEquals("/storage/emulated/0/odk/layers", storageManager.getAbsolutePath(StorageManager.Subdirectory.LAYERS));
        assertEquals("/storage/emulated/0/odk/settings", storageManager.getAbsolutePath(StorageManager.Subdirectory.SETTINGS));
        assertEquals("/storage/emulated/0/odk/.cache/tmp.jpg", storageManager.getTmpFilePath());
        assertEquals("/storage/emulated/0/odk/.cache/tmpDraw.jpg", storageManager.getTmpDrawFilePath());
    }

    @Test
    public void when_scopedStorageUsed_should_externalStoragePathsBeUsed() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk", storageManager.getMainODKDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms", storageManager.getAbsolutePath(StorageManager.Subdirectory.FORMS));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances", storageManager.getAbsolutePath(StorageManager.Subdirectory.INSTANCES));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/metadata", storageManager.getAbsolutePath(StorageManager.Subdirectory.METADATA));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache", storageManager.getAbsolutePath(StorageManager.Subdirectory.CACHE));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/layers", storageManager.getAbsolutePath(StorageManager.Subdirectory.LAYERS));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/settings", storageManager.getAbsolutePath(StorageManager.Subdirectory.SETTINGS));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/tmp.jpg", storageManager.getTmpFilePath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/tmpDraw.jpg", storageManager.getTmpDrawFilePath());
    }

    @Test
    public void when_scopedStorageNotUsed_should_getCacheFilePathMethodReturnAbsolutePath() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getDbPathFromRelativePath("4cd980d50f884362afba842cbff3a798.formdef", StorageManager.Subdirectory.CACHE));
    }

    @Test
    public void when_scopedStorageUsed_should_getCacheFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("4cd980d50f884362afba842cbff3a798.formdef", storageManager.getDbPathFromRelativePath("4cd980d50f884362afba842cbff3a798.formdef", StorageManager.Subdirectory.CACHE));
    }

    @Test
    public void getAbsoluteCacheFilePath_should_returnAbsolutePathToCacheFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getAbsolutePath(StorageManager.Subdirectory.CACHE, "4cd980d50f884362afba842cbff3a798.formdef"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getAbsolutePath(StorageManager.Subdirectory.CACHE, "/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef"));
    }

    @Test
    public void when_scopedStorageNotUsed_should_getFormFilePathMethodReturnAbsolutePath() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk/forms/All widgets.xml", storageManager.getDbPathFromRelativePath("All widgets.xml", StorageManager.Subdirectory.FORMS));
    }

    @Test
    public void when_scopedStorageUsed_should_getFormFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("All widgets.xml", storageManager.getDbPathFromRelativePath("All widgets.xml", StorageManager.Subdirectory.FORMS));
    }

    @Test
    public void getAbsoluteFormFilePath_should_returnAbsolutePathToFormFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml", storageManager.getAbsolutePath(StorageManager.Subdirectory.FORMS, "All widgets.xml"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml", storageManager.getAbsolutePath(StorageManager.Subdirectory.FORMS, "/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml"));
    }

    @Test
    public void getRelativeFormFilePath_should_returnRelativePathToInstanceFile() {
        mockUsingScopedStorage();

        assertEquals("All widgets.xml", storageManager.getRelativeFormFilePath("All widgets.xml"));
        assertEquals("All widgets.xml", storageManager.getRelativeFormFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml"));
    }

    @Test
    public void when_scopedStorageNotUsed_should_getInstanceFilePathMethodReturnAbsolutePath() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getDbPathFromRelativePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", StorageManager.Subdirectory.INSTANCES));
    }

    @Test
    public void when_scopedStorageUsed_should_getInstanceFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getDbPathFromRelativePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", StorageManager.Subdirectory.INSTANCES));
    }

    @Test
    public void getAbsoluteInstanceFilePath_should_returnAbsolutePathToInstanceFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getAbsolutePath(StorageManager.Subdirectory.INSTANCES, "All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getAbsolutePath(StorageManager.Subdirectory.INSTANCES, "/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }

    @Test
    public void getRelativeInstanceFilePath_should_returnRelativePathToInstanceFile() {
        mockUsingScopedStorage();

        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getRelativeInstanceFilePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getRelativeInstanceFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }
}
