package com.kylebarnes.clouddeck.model;

import java.time.LocalDateTime;
import java.util.List;

public record TafData(
        String airportId,
        String issueTime,
        LocalDateTime issueTimeUtc,
        String validPeriod,
        LocalDateTime validFromUtc,
        LocalDateTime validToUtc,
        String rawText,
        List<TafPeriod> periods
) {
}
