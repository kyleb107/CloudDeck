package com.kylebarnes.clouddeck.model;

import java.util.List;

public record TafData(
        String airportId,
        String issueTime,
        String validPeriod,
        String rawText,
        List<TafPeriod> periods
) {
}
