package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;
import com.kylebarnes.clouddeck.model.TimedRouteAssessment;

import java.time.LocalDateTime;
import java.util.List;

public class FlightConditionEvaluator {

    public VfrAssessment assessVfr(MetarData metar) {
        return assessVfr(metar, AppSettings.defaults());
    }

    public VfrAssessment assessVfr(MetarData metar, AppSettings settings) {
        float visibility = metar.visibilitySm();
        int lowestCeiling = lowestCeilingFeet(metar.cloudLayers());

        if (visibility < settings.vfrWarningVisibilitySm() || lowestCeiling < settings.vfrWarningCeilingFt()) {
            return new VfrAssessment(VfrStatusLevel.WARNING, "WARNING: Below VFR minimums - IFR conditions");
        }
        if (visibility < settings.vfrCautionVisibilitySm() || lowestCeiling < settings.vfrCautionCeilingFt()) {
            return new VfrAssessment(VfrStatusLevel.CAUTION, "CAUTION: Marginal VFR conditions");
        }
        return new VfrAssessment(VfrStatusLevel.VFR, "VFR conditions meet minimums");
    }

    public VfrAssessment assessTaf(TafData taf) {
        return assessTaf(taf, AppSettings.defaults());
    }

    public VfrAssessment assessTaf(TafData taf, AppSettings settings) {
        if (taf == null || taf.periods().isEmpty()) {
            return null;
        }

        boolean hasCaution = false;
        for (TafPeriod period : taf.periods()) {
            VfrStatusLevel level = assessForecastPeriod(period, settings);
            if (level == VfrStatusLevel.WARNING) {
                return new VfrAssessment(VfrStatusLevel.WARNING, "TAF includes below-VFR periods");
            }
            if (level == VfrStatusLevel.CAUTION) {
                hasCaution = true;
            }
        }

        if (hasCaution) {
            return new VfrAssessment(VfrStatusLevel.CAUTION, "TAF includes marginal VFR periods");
        }
        return new VfrAssessment(VfrStatusLevel.VFR, "TAF remains VFR through the forecast");
    }

    public RouteAssessment assessRoute(List<AirportWeather> airportWeather, String departureId, String destinationId) {
        return assessRoute(airportWeather, departureId, destinationId, AppSettings.defaults());
    }

    public RouteAssessment assessRoute(List<AirportWeather> airportWeather, String departureId, String destinationId, AppSettings settings) {
        AirportWeather departure = airportWeather.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(departureId))
                .findFirst()
                .orElse(null);
        AirportWeather destination = airportWeather.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(destinationId))
                .findFirst()
                .orElse(null);

        AirportOutlook departureOutlook = assessAirportOutlook(departure, settings);
        AirportOutlook destinationOutlook = assessAirportOutlook(destination, settings);

        if (departureOutlook.level() == VfrStatusLevel.WARNING && destinationOutlook.level() == VfrStatusLevel.WARNING) {
            return new RouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Current or forecast conditions drop below VFR minimums at both "
                            + departureId + " and " + destinationId + "."
            );
        }
        if (departureOutlook.level() == VfrStatusLevel.WARNING) {
            return new RouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Departure " + departureId + " falls below VFR minimums in the current weather or forecast."
            );
        }
        if (destinationOutlook.level() == VfrStatusLevel.WARNING) {
            return new RouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Destination " + destinationId + " falls below VFR minimums in the current weather or forecast."
            );
        }
        if (departureOutlook.level() == VfrStatusLevel.CAUTION && destinationOutlook.level() == VfrStatusLevel.CAUTION) {
            return new RouteAssessment(
                    RouteDecisionLevel.CAUTION,
                    "CAUTION - Marginal conditions appear in the current weather or forecast at both airports."
            );
        }
        if (departureOutlook.level() == VfrStatusLevel.CAUTION) {
            return new RouteAssessment(
                    RouteDecisionLevel.CAUTION,
                    "CAUTION - Departure " + departureId + " shows marginal current weather or forecast periods."
            );
        }
        if (destinationOutlook.level() == VfrStatusLevel.CAUTION) {
            return new RouteAssessment(
                    RouteDecisionLevel.CAUTION,
                    "CAUTION - Destination " + destinationId + " shows marginal current weather or forecast periods."
            );
        }

        return new RouteAssessment(
                RouteDecisionLevel.GO,
                "GO - Current weather and forecast remain VFR at both " + departureId + " and " + destinationId + "."
        );
    }

    public TimedRouteAssessment assessTimedRoute(
            AirportWeather departure,
            AirportWeather destination,
            LocalDateTime departureTimeUtc,
            LocalDateTime arrivalTimeUtc,
            AppSettings settings
    ) {
        VfrAssessment departureForecast = assessForecastAtTime(departure == null ? null : departure.taf(), departureTimeUtc, settings);
        VfrAssessment destinationForecast = assessForecastAtTime(destination == null ? null : destination.taf(), arrivalTimeUtc, settings);

        VfrStatusLevel departureLevel = worstLevel(
                departure == null ? VfrStatusLevel.WARNING : assessVfr(departure.metar(), settings).level(),
                departureForecast == null ? VfrStatusLevel.VFR : departureForecast.level()
        );
        VfrStatusLevel destinationLevel = worstLevel(
                destination == null ? VfrStatusLevel.WARNING : assessVfr(destination.metar(), settings).level(),
                destinationForecast == null ? VfrStatusLevel.VFR : destinationForecast.level()
        );

        if (departureLevel == VfrStatusLevel.WARNING && destinationLevel == VfrStatusLevel.WARNING) {
            return new TimedRouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Departure and destination are below VFR minimums at the planned times.",
                    departureTimeUtc,
                    arrivalTimeUtc,
                    departureForecast,
                    destinationForecast
            );
        }
        if (departureLevel == VfrStatusLevel.WARNING) {
            return new TimedRouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Departure conditions are below VFR minimums at the planned departure time.",
                    departureTimeUtc,
                    arrivalTimeUtc,
                    departureForecast,
                    destinationForecast
            );
        }
        if (destinationLevel == VfrStatusLevel.WARNING) {
            return new TimedRouteAssessment(
                    RouteDecisionLevel.NO_GO,
                    "NO-GO - Destination conditions are below VFR minimums at the planned arrival time.",
                    departureTimeUtc,
                    arrivalTimeUtc,
                    departureForecast,
                    destinationForecast
            );
        }
        if (departureLevel == VfrStatusLevel.CAUTION || destinationLevel == VfrStatusLevel.CAUTION) {
            return new TimedRouteAssessment(
                    RouteDecisionLevel.CAUTION,
                    "CAUTION - Marginal conditions appear at the planned departure or arrival time.",
                    departureTimeUtc,
                    arrivalTimeUtc,
                    departureForecast,
                    destinationForecast
            );
        }

        return new TimedRouteAssessment(
                RouteDecisionLevel.GO,
                "GO - Planned departure and arrival times remain VFR based on current and forecast conditions.",
                departureTimeUtc,
                arrivalTimeUtc,
                departureForecast,
                destinationForecast
        );
    }

    private AirportOutlook assessAirportOutlook(AirportWeather airportWeather, AppSettings settings) {
        if (airportWeather == null) {
            return new AirportOutlook(VfrStatusLevel.WARNING);
        }

        VfrStatusLevel currentLevel = assessVfr(airportWeather.metar(), settings).level();
        VfrAssessment tafAssessment = assessTaf(airportWeather.taf(), settings);
        VfrStatusLevel forecastLevel = tafAssessment == null ? VfrStatusLevel.VFR : tafAssessment.level();
        return new AirportOutlook(worstLevel(currentLevel, forecastLevel));
    }

    private VfrStatusLevel assessForecastPeriod(TafPeriod period, AppSettings settings) {
        float visibility = period.visibilitySm() == null ? Float.MAX_VALUE : period.visibilitySm();
        int lowestCeiling = lowestCeilingFeet(period.cloudLayers());

        if (visibility < settings.vfrWarningVisibilitySm() || lowestCeiling < settings.vfrWarningCeilingFt()) {
            return VfrStatusLevel.WARNING;
        }
        if (visibility < settings.vfrCautionVisibilitySm() || lowestCeiling < settings.vfrCautionCeilingFt()) {
            return VfrStatusLevel.CAUTION;
        }
        return VfrStatusLevel.VFR;
    }

    private VfrAssessment assessForecastAtTime(TafData taf, LocalDateTime timeUtc, AppSettings settings) {
        if (taf == null || timeUtc == null) {
            return null;
        }

        TafPeriod matchingPeriod = taf.periods().stream()
                .filter(period -> period.startTimeUtc() != null && period.endTimeUtc() != null)
                .filter(period -> !timeUtc.isBefore(period.startTimeUtc()) && timeUtc.isBefore(period.endTimeUtc()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (matchingPeriod == null) {
            return null;
        }

        VfrStatusLevel level = assessForecastPeriod(matchingPeriod, settings);
        String message = switch (level) {
            case WARNING -> "Forecast below VFR at planned time";
            case CAUTION -> "Forecast marginal VFR at planned time";
            case VFR -> "Forecast VFR at planned time";
        };
        return new VfrAssessment(level, message);
    }

    private VfrStatusLevel worstLevel(VfrStatusLevel left, VfrStatusLevel right) {
        if (left == VfrStatusLevel.WARNING || right == VfrStatusLevel.WARNING) {
            return VfrStatusLevel.WARNING;
        }
        if (left == VfrStatusLevel.CAUTION || right == VfrStatusLevel.CAUTION) {
            return VfrStatusLevel.CAUTION;
        }
        return VfrStatusLevel.VFR;
    }

    private int lowestCeilingFeet(List<CloudLayer> cloudLayers) {
        if (cloudLayers == null || cloudLayers.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        return cloudLayers.stream()
                .filter(layer -> "BKN".equals(layer.cover()) || "OVC".equals(layer.cover()))
                .mapToInt(CloudLayer::baseFt)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private record AirportOutlook(VfrStatusLevel level) {
    }
}
