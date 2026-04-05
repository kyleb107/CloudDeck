package com.kylebarnes.clouddeck.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SolarTimes(
        LocalDate dateUtc,
        LocalDateTime sunriseUtc,
        LocalDateTime sunsetUtc,
        boolean allDaylight,
        boolean allNight
) {
}
