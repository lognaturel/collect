package org.odk.collect.android.storage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class StorageManagerTest {

    private StorageManager storageManager = spy(StorageManager.class);

    @Before
    public void setup() {
        doReturn("/storage/emulated/0/Android/data/org.odk.collect.android/files").when(storageManager).getPrimaryExternalStorageFilePath();
        doReturn("/storage/emulated/0").when(storageManager).getSecondaryExternalStorageFilePath();
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
        assertEquals("/storage/emulated/0/odk/forms", storageManager.getFormsDirPath());
        assertEquals("/storage/emulated/0/odk/instances", storageManager.getInstancesDirPath());
        assertEquals("/storage/emulated/0/odk/metadata", storageManager.getMetadataDirPath());
        assertEquals("/storage/emulated/0/odk/.cache", storageManager.getCacheDirPath());
        assertEquals("/storage/emulated/0/odk/layers", storageManager.getOfflineLayersDirPath());
        assertEquals("/storage/emulated/0/odk/settings", storageManager.getSettingsDirPath());
        assertEquals("/storage/emulated/0/odk/.cache/tmp.jpg", storageManager.getTmpFilePath());
        assertEquals("/storage/emulated/0/odk/.cache/tmpDraw.jpg", storageManager.getTmpDrawFilePath());
    }

    @Test
    public void when_scopedStorageUsed_should_externalStoragePathsBeUsed() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk", storageManager.getMainODKDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms", storageManager.getFormsDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances", storageManager.getInstancesDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/metadata", storageManager.getMetadataDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache", storageManager.getCacheDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/layers", storageManager.getOfflineLayersDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/settings", storageManager.getSettingsDirPath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/tmp.jpg", storageManager.getTmpFilePath());
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/tmpDraw.jpg", storageManager.getTmpDrawFilePath());
    }

    @Test
    public void when_scopedStorageNotUsed_should_getCacheFilePathMethodReturnAbsolutePath() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getCacheFilePath("4cd980d50f884362afba842cbff3a798.formdef"));
    }

    @Test
    public void when_scopedStorageUsed_should_getCacheFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("4cd980d50f884362afba842cbff3a798.formdef", storageManager.getCacheFilePath("4cd980d50f884362afba842cbff3a798.formdef"));
    }

    @Test
    public void getAbsoluteCacheFilePath_should_returnAbsolutePathToCacheFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getAbsoluteCacheFilePath("4cd980d50f884362afba842cbff3a798.formdef"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef", storageManager.getAbsoluteCacheFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/.cache/4cd980d50f884362afba842cbff3a798.formdef"));
    }

    @Test
    public void when_scopedStorageNotUsed_should_getFormFilePathMethodReturnAbsolutePath() {
        mockUsingSdCard();

        assertEquals("/storage/emulated/0/odk/forms/All widgets.xml", storageManager.getFormFilePath("All widgets.xml"));
    }

    @Test
    public void when_scopedStorageUsed_should_getFormFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("All widgets.xml", storageManager.getFormFilePath("All widgets.xml"));
    }

    @Test
    public void getAbsoluteFormFilePath_should_returnAbsolutePathToFormFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml", storageManager.getAbsoluteFormFilePath("All widgets.xml"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml", storageManager.getAbsoluteFormFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/forms/All widgets.xml"));
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

        assertEquals("/storage/emulated/0/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getInstanceFilePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }

    @Test
    public void when_scopedStorageUsed_should_getInstanceFilePathMethodReturnRelativePath() {
        mockUsingScopedStorage();

        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getInstanceFilePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }

    @Test
    public void getAbsoluteInstanceFilePath_should_returnAbsolutePathToInstanceFile() {
        mockUsingScopedStorage();

        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getAbsoluteInstanceFilePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
        assertEquals("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getAbsoluteInstanceFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }

    @Test
    public void getRelativeInstanceFilePath_should_returnRelativePathToInstanceFile() {
        mockUsingScopedStorage();

        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getRelativeInstanceFilePath("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
        assertEquals("All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml", storageManager.getRelativeInstanceFilePath("/storage/emulated/0/Android/data/org.odk.collect.android/files/odk/instances/All widgets_2020-01-20_13-54-11/All widgets_2020-01-20_13-54-11.xml"));
    }
}
