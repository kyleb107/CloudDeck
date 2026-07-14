package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AppSettings;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.RoutePlan;
import com.kylebarnes.clouddeck.model.WindVector;

public class FlightPlanningService {
    private static final double EARTH_RADIUS_NM = 3440.065;

    public RoutePlan planDirectRoute(
            AirportInfo departure,
            AirportInfo destination,
            AircraftProfile aircraftProfile,
            AppSettings appSettings,
            WindVector routeWind
    ) {
        if (departure == null || destination == null || aircraftProfile == null || appSettings == null) {
            return null;
        }
        if ((departure.latitudeDeg() == 0 && departure.longitudeDeg() == 0)
                || (destination.latitudeDeg() == 0 && destination.longitudeDeg() == 0)) {
            return null;
        }

        double distanceNm = calculateDistanceNm(
                departure.latitudeDeg(),
                departure.longitudeDeg(),
                destination.latitudeDeg(),
                destination.longitudeDeg()
        );

        double trueCourse = calculateTrueCourse(
                departure.latitudeDeg(),
                departure.longitudeDeg(),
                destination.latitudeDeg(),
                destination.longitudeDeg()
        );

        double groundspeedKts;
        double wcaDeg = 0;
        double trueHeading = trueCourse;

        // Calculate Wind Correction Angle (WCA) and actual Groundspeed if winds aloft are provided
        if (routeWind != null && routeWind.speedKts() > 0 && aircraftProfile.cruiseSpeedKts() > 0) {
            double trueAirspeed = aircraftProfile.cruiseSpeedKts();
            double normalizedWindDirection = ((routeWind.direction() % 360) + 360) % 360;
            double windAngle = Math.toRadians(normalizedWindDirection - trueCourse);
            double crosswind = routeWind.speedKts() * Math.sin(windAngle);
            double headwind = routeWind.speedKts() * Math.cos(windAngle);

            double ratio = Math.max(-1.0, Math.min(1.0, crosswind / trueAirspeed));
            double wcaRad = Math.asin(ratio);
            wcaDeg = Math.toDegrees(wcaRad);

            trueHeading = (trueCourse + wcaDeg) % 360;
            if (trueHeading < 0) trueHeading += 360;

            groundspeedKts = (trueAirspeed * Math.cos(wcaRad)) - headwind;

            // Failsafe in case of massive hurricane-force headwinds exceeding true airspeed
            if (Double.isNaN(groundspeedKts) || groundspeedKts < 0) groundspeedKts = 1;
        } else {
            // Fallback to legacy manual adjustment if no winds aloft are provided
            groundspeedKts = Math.max(40.0, aircraftProfile.cruiseSpeedKts() + appSettings.groundspeedAdjustmentKts());
        }

        double estimatedTimeHours = distanceNm / groundspeedKts;
        double airborneFuelGallons = estimatedTimeHours * aircraftProfile.fuelBurnGph();
        double tripFuelGallons = airborneFuelGallons + appSettings.taxiFuelGallons() + appSettings.climbFuelGallons();
        double reserveRemainingGallons = aircraftProfile.usableFuelGallons() - tripFuelGallons;
        boolean reserveSatisfied = reserveRemainingGallons >= aircraftProfile.reserveFuelGallons();

        return new RoutePlan(
                departure,
                destination,
                distanceNm,
                trueCourse,
                trueHeading,
                wcaDeg,
                groundspeedKts,
                estimatedTimeHours,
                airborneFuelGallons,
                appSettings.taxiFuelGallons(),
                appSettings.climbFuelGallons(),
                tripFuelGallons,
                reserveRemainingGallons,
                reserveSatisfied
        );
    }

    public RoutePlan planDirectRoute(
            AirportInfo departure,
            AirportInfo destination,
            AircraftProfile aircraftProfile,
            AppSettings appSettings
    ) {
        return planDirectRoute(departure, destination, aircraftProfile, appSettings, null);
    }

    private double calculateDistanceNm(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_NM * c;
    }

    private double calculateTrueCourse(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);
        double bearing = Math.atan2(y, x);
        return (Math.toDegrees(bearing) + 360) % 360;
    }
}
