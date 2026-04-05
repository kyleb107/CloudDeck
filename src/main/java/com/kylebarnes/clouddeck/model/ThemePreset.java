package com.kylebarnes.clouddeck.model;

public enum ThemePreset {
    NIGHTFALL("Nightfall"),
    CLEARSKY("ClearSky");

    private final String displayName;

    ThemePreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
