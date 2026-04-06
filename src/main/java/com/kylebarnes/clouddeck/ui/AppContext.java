package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.data.AviationWeatherClient;
import com.kylebarnes.clouddeck.data.MetarParser;
import com.kylebarnes.clouddeck.data.OurAirportsRepository;
import com.kylebarnes.clouddeck.data.TafClient;
import com.kylebarnes.clouddeck.data.TafParser;
import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AlternateAirportOption;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.service.AlternateAirportService;
import com.kylebarnes.clouddeck.service.AirportDiagramService;
import com.kylebarnes.clouddeck.service.BriefingExportService;
import com.kylebarnes.clouddeck.service.DensityAltitudeService;
import com.kylebarnes.clouddeck.service.FaaChartLinkService;
import com.kylebarnes.clouddeck.service.FlightConditionEvaluator;
import com.kylebarnes.clouddeck.service.FlightPlanningService;
import com.kylebarnes.clouddeck.service.MetarTrendService;
import com.kylebarnes.clouddeck.service.OperationalAlertService;
import com.kylebarnes.clouddeck.service.RunwayAnalysisService;
import com.kylebarnes.clouddeck.service.SolarCalculatorService;
import com.kylebarnes.clouddeck.service.WeatherService;
import com.kylebarnes.clouddeck.storage.AircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.FavoritesRepository;
import com.kylebarnes.clouddeck.storage.LocalAircraftProfileRepository;
import com.kylebarnes.clouddeck.storage.LocalFavoritesRepository;
import com.kylebarnes.clouddeck.storage.LocalRouteHistoryRepository;
import com.kylebarnes.clouddeck.storage.LocalSettingsRepository;
import com.kylebarnes.clouddeck.storage.RouteHistoryRepository;
import com.kylebarnes.clouddeck.storage.SettingsRepository;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AppContext {

    // Services
    final OurAirportsRepository airportsRepository = new OurAirportsRepository();
    final WeatherService weatherService = new WeatherService(
            new AviationWeatherClient(),
            new TafClient(),
            new MetarParser(),
            new TafParser(),
            airportsRepository
    );
    final FlightConditionEvaluator flightConditionEvaluator = new FlightConditionEvaluator();
    final RunwayAnalysisService runwayAnalysisService = new RunwayAnalysisService();
    final FlightPlanningService flightPlanningService = new FlightPlanningService();
    final BriefingExportService briefingExportService = new BriefingExportService();
    final FaaChartLinkService faaChartLinkService = new FaaChartLinkService();
    final AirportDiagramService airportDiagramService = new AirportDiagramService();
    final MetarTrendService metarTrendService = new MetarTrendService();
    final OperationalAlertService operationalAlertService = new OperationalAlertService();
    final DensityAltitudeService densityAltitudeService = new DensityAltitudeService();
    final AlternateAirportService alternateAirportService;
    final SolarCalculatorService solarCalculatorService = new SolarCalculatorService();

    // Repos
    final FavoritesRepository favoritesRepository = new LocalFavoritesRepository();
    final AircraftProfileRepository aircraftProfileRepository = new LocalAircraftProfileRepository();
    final SettingsRepository settingsRepository = new LocalSettingsRepository();
    final RouteHistoryRepository routeHistoryRepository = new LocalRouteHistoryRepository();

    // Executor
    final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("clouddeck-background");
        return thread;
    });

    // Mutable settings/theme
    AppSettings appSettings;
    ThemePalette themePalette;

    // Shared persistent UI
    final ComboBox<AircraftProfile> aircraftSelector = new ComboBox<>();
    final Label aircraftHeroSummary = new Label();
    final Label aircraftHeroNote = new Label();
    final VBox favoritesBar = new VBox(6);
    final VBox weatherCardsContainer = new VBox(14);
    final VBox routeResultsBox = new VBox(12);
    final VBox recentRoutesBox = new VBox(8);
    final VBox aircraftSummaryBox = new VBox(8);

    // Async caches
    final Map<String, Image> airportDiagramImageCache = new ConcurrentHashMap<>();
    final Map<String, String> airportDiagramStatusCache = new ConcurrentHashMap<>();
    final Set<String> airportDiagramLoading = ConcurrentHashMap.newKeySet();
    final Map<String, List<MetarData>> metarHistoryCache = new ConcurrentHashMap<>();
    final Map<String, String> metarHistoryStatusCache = new ConcurrentHashMap<>();
    final Set<String> metarHistoryLoading = ConcurrentHashMap.newKeySet();

    // Data state
    List<AirportWeather> latestWeatherResults = List.of();
    List<AirportWeather> latestRouteResults = List.of();
    List<AlternateAirportOption> latestAlternateOptions = List.of();
    boolean alternateSuggestionsLoading;
    String alternateSuggestionsStatus = "";
    String settingsStatusMessage = "";
    String latestRouteDeparture;
    String latestRouteDestination;

    // Callbacks set by view controllers
    Runnable rerenderWeatherCards;
    Runnable rerenderRouteResults;

    AppContext() {
        alternateAirportService = new AlternateAirportService(
                airportsRepository,
                weatherService,
                flightConditionEvaluator,
                runwayAnalysisService
        );
    }

    <T> void runAsync(CheckedSupplier<T> supplier, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, backgroundExecutor).whenComplete((result, throwable) -> Platform.runLater(() -> {
            if (throwable == null) {
                onSuccess.accept(result);
            } else {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                onError.accept(cause);
            }
        }));
    }

    String normalizeAirportId(String airportId) {
        return airportId == null ? "" : airportId.trim().toUpperCase();
    }

    void invalidateAlternateSuggestions() {
        latestAlternateOptions = List.of();
        alternateSuggestionsLoading = false;
        alternateSuggestionsStatus = "";
    }

    void loadMetarHistoryAsync(String airportId) {
        String normalizedAirportId = normalizeAirportId(airportId);
        if (!metarHistoryLoading.add(normalizedAirportId)) {
            return;
        }

        runAsync(
                () -> weatherService.fetchMetarHistory(normalizedAirportId, 12),
                history -> {
                    metarHistoryLoading.remove(normalizedAirportId);
                    metarHistoryCache.put(normalizedAirportId, history);
                    if (history.isEmpty()) {
                        metarHistoryStatusCache.put(normalizedAirportId, "FAA returned no recent METAR history for this airport.");
                    } else {
                        metarHistoryStatusCache.remove(normalizedAirportId);
                    }
                    if (rerenderWeatherCards != null) rerenderWeatherCards.run();
                    if (rerenderRouteResults != null) rerenderRouteResults.run();
                },
                throwable -> {
                    metarHistoryLoading.remove(normalizedAirportId);
                    metarHistoryStatusCache.put(normalizedAirportId, "Could not load recent METAR history: " + throwable.getMessage());
                    if (rerenderWeatherCards != null) rerenderWeatherCards.run();
                    if (rerenderRouteResults != null) rerenderRouteResults.run();
                }
        );
    }

    void resetLazyHistoryStatuses(List<AirportWeather> airportWeather) {
        for (AirportWeather weather : airportWeather) {
            String airportId = normalizeAirportId(weather.metar().airportId());
            if (!metarHistoryCache.containsKey(airportId) && !metarHistoryLoading.contains(airportId)) {
                metarHistoryStatusCache.remove(airportId);
            }
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
