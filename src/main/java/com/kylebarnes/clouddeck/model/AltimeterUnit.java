package com.kylebarnes.clouddeck.model;

public enum AltimeterUnit {
    IN_HG("inHg"),
    HPA("hPa");

    private final String displayName;

    AltimeterUnit(String displayName) {
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
