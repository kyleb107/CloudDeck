package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.RecentRouteEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocalRouteHistoryRepository implements RouteHistoryRepository {
    private static final Path ROUTE_HISTORY_FILE = Path.of(
            System.getProperty("user.home"),
            ".clouddeck",
            "recent_routes.tsv"
    );
    private static final int MAX_RECENT_ROUTES = 8;

    @Override
    public synchronized List<RecentRouteEntry> loadRecentRoutes() {
        if (!Files.exists(ROUTE_HISTORY_FILE)) {
            return List.of();
        }

        List<RecentRouteEntry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(ROUTE_HISTORY_FILE, StandardCharsets.UTF_8)) {
                RecentRouteEntry entry = parseLine(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException exception) {
            System.out.println("Could not load route history: " + exception.getMessage());
            return List.of();
        }

        entries.sort(Comparator.comparing(RecentRouteEntry::lastUsedUtc).reversed());
        return entries;
    }

    @Override
    public synchronized void saveRecentRoute(String departureAirport, String destinationAirport, LocalDateTime plannedDepartureUtc) {
        String normalizedDeparture = normalizeAirport(departureAirport);
        String normalizedDestination = normalizeAirport(destinationAirport);
        if (normalizedDeparture.isBlank() || normalizedDestination.isBlank() || plannedDepartureUtc == null) {
            return;
        }

        Map<String, RecentRouteEntry> byRoute = new LinkedHashMap<>();
        for (RecentRouteEntry entry : loadRecentRoutes()) {
            byRoute.put(routeKey(entry.departureAirport(), entry.destinationAirport()), entry);
        }

        byRoute.put(
                routeKey(normalizedDeparture, normalizedDestination),
                new RecentRouteEntry(normalizedDeparture, normalizedDestination, plannedDepartureUtc, LocalDateTime.now())
        );

        List<RecentRouteEntry> entries = new ArrayList<>(byRoute.values());
        entries.sort(Comparator.comparing(RecentRouteEntry::lastUsedUtc).reversed());
        if (entries.size() > MAX_RECENT_ROUTES) {
            entries = new ArrayList<>(entries.subList(0, MAX_RECENT_ROUTES));
        }

        try {
            Files.createDirectories(ROUTE_HISTORY_FILE.getParent());
            List<String> lines = entries.stream()
                    .map(this::serialize)
                    .toList();
            Files.write(ROUTE_HISTORY_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.out.println("Could not save route history: " + exception.getMessage());
        }
    }

    private RecentRouteEntry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String[] parts = line.split("\t");
        if (parts.length != 4) {
            return null;
        }

        try {
            return new RecentRouteEntry(
                    normalizeAirport(parts[0]),
                    normalizeAirport(parts[1]),
                    LocalDateTime.parse(parts[2].trim()),
                    LocalDateTime.parse(parts[3].trim())
            );
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String serialize(RecentRouteEntry entry) {
        return entry.departureAirport() + "\t"
                + entry.destinationAirport() + "\t"
                + entry.plannedDepartureUtc() + "\t"
                + entry.lastUsedUtc();
    }

    private String normalizeAirport(String airport) {
        return airport == null ? "" : airport.trim().toUpperCase(Locale.US);
    }

    private String routeKey(String departureAirport, String destinationAirport) {
        return departureAirport + "->" + destinationAirport;
    }
}
