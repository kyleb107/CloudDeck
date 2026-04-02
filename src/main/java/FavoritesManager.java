/* Designed by: Kyle Barnes
   Manages saving and loading favorite airport ICAO IDs to a local file
 */

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FavoritesManager {

    //===== CONSTANTS =====
    //saved in the user's home directory ex: C:\Users\Kyle\clouddeck_favorites.txt
    private static final String FAVORITES_FILE =
            System.getProperty("user.home") + "/clouddeck_favorites.txt";

    //===== LOAD =====
    //reads saved favorites from file, returns empty list if file doesn't exist
    public static List<String> loadFavorites() {
        List<String> favorites = new ArrayList<>();
        File file = new File(FAVORITES_FILE);

        if (!file.exists()) return favorites;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String icao = line.trim().toUpperCase();
                if (!icao.isEmpty() && !favorites.contains(icao)) {
                    favorites.add(icao);
                }
            }
        }
        catch (IOException e) {
            System.out.println("Could not load favorites: " + e.getMessage());
        }

        return favorites;
    }

    //===== SAVE =====
    //adds an airport to favorites if not already saved
    public static boolean addFavorite(String icaoId) {
        List<String> favorites = loadFavorites();
        String icao = icaoId.trim().toUpperCase();

        if (favorites.contains(icao)) return false;

        favorites.add(icao);
        saveFavorites(favorites);
        return true;
    }

    //===== REMOVE =====
    //removes an airport from favorites
    public static boolean removeFavorite(String icaoId) {
        List<String> favorites = loadFavorites();
        String icao = icaoId.trim().toUpperCase();

        if (!favorites.contains(icao)) return false;

        favorites.remove(icao);
        saveFavorites(favorites);
        return true;
    }

    //===== IS FAVORITE =====
    public static boolean isFavorite(String icaoId) {
        return loadFavorites().contains(icaoId.trim().toUpperCase());
    }

    //===== PRIVATE HELPER =====
    //writes the full favorites list back to the file
    private static void saveFavorites(List<String> favorites) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FAVORITES_FILE))) {
            for (String icao : favorites) {
                writer.write(icao);
                writer.newLine();
            }
        }
        catch (IOException e) {
            System.out.println("Could not save favorites: " + e.getMessage());
        }
    }
}