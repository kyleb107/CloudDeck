/* Designed by: Kyle Barnes
   Prompts user for an ICAO airport ID & displays METAR data
 */

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        MetarFetcher fetcher = new MetarFetcher();
        MetarParser parser = new MetarParser();

        System.out.println("=== Aviation Weather Tracker ===");

        while (true) {
            System.out.print("Enter ICAO station ID (or 'quit' to exit): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit")) {
                break;
            }

            //output parsed & raw metar (if successful)
            try {
                MetarData metar = parser.parse(fetcher.fetchRaw(input));
                System.out.println("\n" + metar.toString());
                System.out.println("Raw METAR: " + metar.getRawOb() + "\n");
            }
            //error, invalid metar
            catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("Goodbye.");
    }
}
