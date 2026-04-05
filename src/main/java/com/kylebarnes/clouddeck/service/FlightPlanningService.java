package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.RoutePlan;

public class FlightPlanningService {
    private static final double EARTH_RADIUS_NM = 3440.065;

    public RoutePlan planDirectRoute(AirportInfo departure, AirportInfo destination, AircraftProfile aircraftProfile) {
        if (departure == null || destination == null || aircraftProfile == null) {
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
        double estimatedTimeHours = distanceNm / aircraftProfile.cruiseSpeedKts();
        double tripFuelGallons = estimatedTimeHours * aircraftProfile.fuelBurnGph();
        double reserveRemainingGallons = aircraftProfile.usableFuelGallons() - tripFuelGallons;
        boolean reserveSatisfied = reserveRemainingGallons >= aircraftProfile.reserveFuelGallons();

        return new RoutePlan(
                departure,
                destination,
                distanceNm,
                estimatedTimeHours,
                tripFuelGallons,
                reserveRemainingGallons,
                reserveSatisfied
        );
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
}
