package com.kylebarnes.clouddeck.model;

public record AirportInfo(
        String ident,
        String name,
        String municipality,
        String isoRegion,
        int elevationFt,
        String airportType,
        double latitudeDeg,
        double longitudeDeg
) {
}
