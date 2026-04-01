/* Designed by: Kyle Barnes
   Terminal interface for CloudDeck — prompts user for ICAO IDs and displays METAR data
   Note: this is the legacy terminal version, MainApp.java is the primary GUI entry point
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.json.JSONArray;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        MetarFetcher fetcher = new MetarFetcher();
        MetarParser parser = new MetarParser();

        System.out.println("=== CloudDeck Terminal ===");

        while (true) {

            //===== INPUT =====
            System.out.print("Enter ICAO station ID (or 'quit' to exit): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit")) break;

            if (input.isEmpty()) {
                System.out.println("No input entered, please try again.");
                continue;
            }

            //===== FETCH AND DISPLAY =====
            try {
                JSONArray results = fetcher.fetchRaw(input);
                List<MetarData> metarList = new ArrayList<>();

                for (int i = 0; i < results.length(); i++) {
                    metarList.add(parser.parse(results.getJSONObject(i)));
                }

                //sort results to match user input order
                List<String> inputOrder = Arrays.asList(input.toUpperCase().split(","));
                metarList.sort((a, b) ->
                        inputOrder.indexOf(a.getAirportId()) - inputOrder.indexOf(b.getAirportId()));

                //display all METARs first
                for (MetarData metar : metarList) {
                    System.out.println("\n" + metar.toString());
                    System.out.println("Raw METAR: " + metar.getRawOb() + "\n");
                }

                //===== CROSSWIND CALCULATIONS =====
                for (MetarData metar : metarList) {
                    System.out.println("\nCrosswind calculation for " + metar.getAirportId() + ":");
                    String runwayRepeat = "y";

                    while (runwayRepeat.equalsIgnoreCase("y")) {
                        System.out.print("Enter runway heading (1-36 or 0 to skip): ");
                        int runway = 0;

                        try {
                            runway = Integer.parseInt(scanner.nextLine().trim());
                            if (runway < 0 || runway > 36) {
                                System.out.println("Invalid runway — must be between 1 and 36.");
                                break;
                            }
                        }
                        catch (NumberFormatException e) {
                            System.out.println("Invalid input — please enter a number between 1 and 36.");
                            break;
                        }

                        if (runway != 0) {
                            CrosswindCalculator.printComponents(
                                    runway * 10, metar.getWindDir(), metar.getWindSpeed());
                            System.out.print("Another runway? (y/n): ");
                            runwayRepeat = scanner.nextLine().trim();
                        }
                        else {
                            break;
                        }
                    }
                }
            }
            catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        //===== EXIT =====
        scanner.close();
        System.out.println("Goodbye. Thank you for using CloudDeck!");
    }
}