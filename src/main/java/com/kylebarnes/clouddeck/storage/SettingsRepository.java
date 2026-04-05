package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.AppSettings;

public interface SettingsRepository {
    AppSettings loadSettings();

    void saveSettings(AppSettings settings);
}
