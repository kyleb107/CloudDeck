package com.kylebarnes.clouddeck.model;

public record AirportSuggestion(String ident, String name, String municipality) {

    public String displayLabel() {
        if (municipality == null || municipality.isBlank()) {
            return ident + "  -  " + name;
        }
        return ident + "  -  " + name + ", " + municipality;
    }
}
