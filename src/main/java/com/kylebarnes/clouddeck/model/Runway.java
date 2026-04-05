package com.kylebarnes.clouddeck.model;

public record Runway(String ident, int heading) {

    @Override
    public String toString() {
        return ident;
    }
}
