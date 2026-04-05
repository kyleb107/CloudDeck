package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.RecentRouteEntry;

import java.time.LocalDateTime;
import java.util.List;

public interface RouteHistoryRepository {
    List<RecentRouteEntry> loadRecentRoutes();

    void saveRecentRoute(String departureAirport, String destinationAirport, LocalDateTime plannedDepartureUtc);
}
