package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.AircraftProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocalAircraftProfileRepository implements AircraftProfileRepository {
    private static final Path PROFILES_FILE = Path.of(
            System.getProperty("user.home"),
            ".clouddeck",
            "aircraft_profiles.tsv"
    );

    @Override
    public synchronized List<AircraftProfile> loadProfiles() {
        if (!Files.exists(PROFILES_FILE)) {
            return defaultProfiles();
        }

        try {
            Map<String, AircraftProfile> profiles = new LinkedHashMap<>();
            for (String line : Files.readAllLines(PROFILES_FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split("\t", -1);
                if (parts.length < 7) {
                    continue;
                }

                try {
                    AircraftProfile profile = new AircraftProfile(
                            parts[0],
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4]),
                            Double.parseDouble(parts[5]),
                            parts[6]
                    );
                    profiles.put(normalize(profile.name()), profile);
                } catch (NumberFormatException ignored) {
                }
            }

            if (profiles.isEmpty()) {
                return defaultProfiles();
            }

            return profiles.values().stream()
                    .sorted(Comparator.comparing(AircraftProfile::name))
                    .toList();
        } catch (IOException exception) {
            System.out.println("Could not load aircraft profiles: " + exception.getMessage());
            return defaultProfiles();
        }
    }

    @Override
    public synchronized boolean saveProfile(AircraftProfile profile) {
        if (profile == null || normalize(profile.name()).isEmpty()) {
            return false;
        }

        List<AircraftProfile> existingProfiles = new ArrayList<>(loadProfiles());
        existingProfiles.removeIf(existing -> normalize(existing.name()).equals(normalize(profile.name())));
        existingProfiles.add(profile);
        persist(existingProfiles);
        return true;
    }

    @Override
    public synchronized boolean removeProfile(String profileName) {
        List<AircraftProfile> existingProfiles = new ArrayList<>(loadProfiles());
        boolean removed = existingProfiles.removeIf(profile -> normalize(profile.name()).equals(normalize(profileName)));
        if (!removed) {
            return false;
        }

        persist(existingProfiles);
        return true;
    }

    private void persist(List<AircraftProfile> profiles) {
        try {
            Files.createDirectories(PROFILES_FILE.getParent());
            List<String> lines = profiles.stream()
                    .sorted(Comparator.comparing(AircraftProfile::name))
                    .map(profile -> String.join("\t",
                            profile.name(),
                            String.valueOf(profile.cruiseSpeedKts()),
                            String.valueOf(profile.fuelBurnGph()),
                            String.valueOf(profile.usableFuelGallons()),
                            String.valueOf(profile.reserveFuelGallons()),
                            String.valueOf(profile.maxCrosswindKts()),
                            profile.notes() == null ? "" : profile.notes().replace("\t", " ").trim()
                    ))
                    .toList();
            Files.write(PROFILES_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.out.println("Could not save aircraft profiles: " + exception.getMessage());
        }
    }

    private List<AircraftProfile> defaultProfiles() {
        return List.of(
                new AircraftProfile("C172 Trainer", 110, 8.5, 53, 12, 12, "Solid default trainer profile"),
                new AircraftProfile("PA-28 Archer", 115, 10.0, 48, 12, 15, "Example personal-use profile")
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }
}
