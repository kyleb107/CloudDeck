package com.kylebarnes.clouddeck.cli;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.service.CrosswindCalculator;
import com.kylebarnes.clouddeck.service.WeatherService;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        WeatherService weatherService = new WeatherService(
                new AviationWeatherClient(),
                new TafClient(),
                new MetarParser(),
                new TafParser(),
                new OurAirportsRepository()
        );

        System.out.println("=== CloudDeck Terminal ===");

        while (true) {
            System.out.print("Enter ICAO station ID (or 'quit' to exit): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit")) {
                break;
            }
            if (input.isEmpty()) {
                System.out.println("No input entered, please try again.");
                continue;
            }

            try {
                List<AirportWeather> airportWeather = weatherService.fetchAirportWeather(input);
                for (AirportWeather weather : airportWeather) {
                    System.out.println();
                    System.out.println(weather.metar());
                    System.out.println("Raw METAR: " + weather.metar().rawObservation());
                    if (weather.taf() != null) {
                        System.out.println("Raw TAF: " + weather.taf().rawText());
                    }
                    System.out.println();
                }

                for (AirportWeather weather : airportWeather) {
                    System.out.println("Crosswind calculation for " + weather.metar().airportId() + ":");
                    String runwayRepeat = "y";

                    while (runwayRepeat.equalsIgnoreCase("y")) {
                        System.out.print("Enter runway heading (1-36 or 0 to skip): ");

                        try {
                            int runway = Integer.parseInt(scanner.nextLine().trim());
                            if (runway < 0 || runway > 36) {
                                System.out.println("Invalid runway - must be between 1 and 36.");
                                break;
                            }

                            if (runway == 0) {
                                break;
                            }

                            System.out.println(CrosswindCalculator.formatTerminalOutput(
                                    runway * 10,
                                    weather.metar().windDir(),
                                    weather.metar().windSpeed()
                            ));
                            System.out.print("Another runway? (y/n): ");
                            runwayRepeat = scanner.nextLine().trim();
                        } catch (NumberFormatException exception) {
                            System.out.println("Invalid input - please enter a number between 1 and 36.");
                            break;
                        }
                    }
                }
            } catch (Exception exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        }

        scanner.close();
        System.out.println("Goodbye. Thank you for using CloudDeck!");
    }
}
