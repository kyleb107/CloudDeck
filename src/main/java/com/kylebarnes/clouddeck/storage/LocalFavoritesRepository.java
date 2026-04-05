package com.kylebarnes.clouddeck.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LocalFavoritesRepository implements FavoritesRepository {
    private static final Path MODERN_FAVORITES_FILE = Path.of(
            System.getProperty("user.home"),
            ".clouddeck",
            "favorites.txt"
    );
    private static final Path LEGACY_FAVORITES_FILE = Path.of(
            System.getProperty("user.home"),
            "clouddeck_favorites.txt"
    );

    @Override
    public synchronized List<String> loadFavorites() {
        Path source = Files.exists(MODERN_FAVORITES_FILE) ? MODERN_FAVORITES_FILE : LEGACY_FAVORITES_FILE;
        if (!Files.exists(source)) {
            return List.of();
        }

        try {
            Set<String> favorites = new LinkedHashSet<>();
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String favorite = normalize(line);
                if (!favorite.isEmpty()) {
                    favorites.add(favorite);
                }
            }
            return new ArrayList<>(favorites);
        } catch (IOException exception) {
            System.out.println("Could not load favorites: " + exception.getMessage());
            return List.of();
        }
    }

    @Override
    public synchronized boolean addFavorite(String favorite) {
        List<String> favorites = new ArrayList<>(loadFavorites());
        String normalizedFavorite = normalize(favorite);
        if (normalizedFavorite.isEmpty() || favorites.contains(normalizedFavorite)) {
            return false;
        }

        favorites.add(normalizedFavorite);
        saveFavorites(favorites);
        return true;
    }

    @Override
    public synchronized boolean removeFavorite(String favorite) {
        List<String> favorites = new ArrayList<>(loadFavorites());
        String normalizedFavorite = normalize(favorite);
        if (!favorites.remove(normalizedFavorite)) {
            return false;
        }

        saveFavorites(favorites);
        return true;
    }

    @Override
    public synchronized boolean isFavorite(String favorite) {
        return loadFavorites().contains(normalize(favorite));
    }

    private void saveFavorites(List<String> favorites) {
        try {
            Files.createDirectories(MODERN_FAVORITES_FILE.getParent());
            Files.write(MODERN_FAVORITES_FILE, favorites, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.out.println("Could not save favorites: " + exception.getMessage());
        }
    }

    private String normalize(String favorite) {
        if (favorite == null) {
            return "";
        }
        return favorite.trim().toUpperCase(Locale.US);
    }
}
