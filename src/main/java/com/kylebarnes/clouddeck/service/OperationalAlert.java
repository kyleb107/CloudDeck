package com.kylebarnes.clouddeck.service;

public record OperationalAlert(
        VfrStatusLevel level,
        String title,
        String detail
) {
}
