package com.kylebarnes.clouddeck.model;

public enum WindUnit {
    KNOTS("Knots"),
    MILES_PER_HOUR("MPH"),
    KILOMETERS_PER_HOUR("KM/H");

    private final String displayName;

    WindUnit(String displayName) {
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
