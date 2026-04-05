package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.AirportWeather;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;

import java.util.List;

public class FlightConditionEvaluator {

    public VfrAssessment assessVfr(MetarData metar) {
        float visibility = metar.visibilitySm();
        int lowestCeiling = lowestCeilingFeet(metar.cloudLayers());

        if (visibility < 3.0f || lowestCeiling < 1000) {
            return new VfrAssessment(VfrStatusLevel.WARNING, "WARNING: Below VFR minimums - IFR conditions");
        }
        if (visibility < 5.0f || lowestCeiling < 3000) {
            return new VfrAssessment(VfrStatusLevel.CAUTION, "CAUTION: Marginal VFR conditions");
        }
        return new VfrAssessment(VfrStatusLevel.VFR, "VFR conditions meet minimums");
    }

    public VfrAssessment assessTaf(TafData taf) {
        if (taf == null || taf.periods().isEmpty()) {
            return null;
        }

        boolean hasCaution = false;
        for (TafPeriod period : taf.periods()) {
            VfrStatusLevel level = assessForecastPeriod(period);
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
        AirportWeather departure = airportWeather.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(departureId))
                .findFirst()
                .orElse(null);
        AirportWeather destination = airportWeather.stream()
                .filter(weather -> weather.metar().airportId().equalsIgnoreCase(destinationId))
                .findFirst()
                .orElse(null);

        AirportOutlook departureOutlook = assessAirportOutlook(departure);
        AirportOutlook destinationOutlook = assessAirportOutlook(destination);

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

    private AirportOutlook assessAirportOutlook(AirportWeather airportWeather) {
        if (airportWeather == null) {
            return new AirportOutlook(VfrStatusLevel.WARNING);
        }

        VfrStatusLevel currentLevel = assessVfr(airportWeather.metar()).level();
        VfrAssessment tafAssessment = assessTaf(airportWeather.taf());
        VfrStatusLevel forecastLevel = tafAssessment == null ? VfrStatusLevel.VFR : tafAssessment.level();
        return new AirportOutlook(worstLevel(currentLevel, forecastLevel));
    }

    private VfrStatusLevel assessForecastPeriod(TafPeriod period) {
        float visibility = period.visibilitySm() == null ? Float.MAX_VALUE : period.visibilitySm();
        int lowestCeiling = lowestCeilingFeet(period.cloudLayers());

        if (visibility < 3.0f || lowestCeiling < 1000) {
            return VfrStatusLevel.WARNING;
        }
        if (visibility < 5.0f || lowestCeiling < 3000) {
            return VfrStatusLevel.CAUTION;
        }
        return VfrStatusLevel.VFR;
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
