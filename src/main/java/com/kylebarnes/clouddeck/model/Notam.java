package com.kylebarnes.clouddeck.model;

public record Notam(
        String id,
        String rawText,
        String type,
        String issued,
        String effectiveStart,
        String effectiveEnd
) {
}