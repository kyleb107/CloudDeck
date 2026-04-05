package com.kylebarnes.clouddeck.model;

public record AircraftProfile(
        String name,
        double cruiseSpeedKts,
        double fuelBurnGph,
        double usableFuelGallons,
        double reserveFuelGallons,
        double maxCrosswindKts,
        String notes
) {

    public String displayLabel() {
        return name + "  |  " + (int) cruiseSpeedKts + " kt  |  " + String.format("%.1f", maxCrosswindKts) + " kt XW";
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
