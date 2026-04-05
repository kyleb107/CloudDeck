package com.kylebarnes.clouddeck.storage;

import java.util.List;

public interface FavoritesRepository {
    List<String> loadFavorites();

    boolean addFavorite(String favorite);

    boolean removeFavorite(String favorite);

    boolean isFavorite(String favorite);
}
