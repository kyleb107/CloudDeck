package com.kylebarnes.clouddeck.model;

import java.time.LocalDateTime;

public record RecentRouteEntry(
        String departureAirport,
        String destinationAirport,
        LocalDateTime plannedDepartureUtc,
        LocalDateTime lastUsedUtc
) {
}
