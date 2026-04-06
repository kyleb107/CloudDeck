package com.kylebarnes.clouddeck.model;

public enum TimeDisplayMode {
    UTC("UTC"),
    LOCAL("Local");

    private final String displayName;

    TimeDisplayMode(String displayName) {
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
