package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.SolarTimes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class SolarCalculatorService {
    private static final double ZENITH = 90.833;

    public SolarTimes calculate(AirportInfo airportInfo, LocalDate dateUtc) {
        if (airportInfo == null || dateUtc == null) {
            return null;
        }
        if (airportInfo.latitudeDeg() == 0 && airportInfo.longitudeDeg() == 0) {
            return null;
        }

        Double sunriseHours = calculateUtcHours(dateUtc, airportInfo.latitudeDeg(), airportInfo.longitudeDeg(), true);
        Double sunsetHours = calculateUtcHours(dateUtc, airportInfo.latitudeDeg(), airportInfo.longitudeDeg(), false);

        if (sunriseHours == null && sunsetHours == null) {
            double middayCosH = cosineLocalHourAngle(dateUtc, airportInfo.latitudeDeg(), airportInfo.longitudeDeg(), false);
            if (middayCosH < -1) {
                return new SolarTimes(dateUtc, null, null, true, false);
            }
            return new SolarTimes(dateUtc, null, null, false, true);
        }
        if (sunriseHours == null || sunsetHours == null) {
            return null;
        }

        return new SolarTimes(
                dateUtc,
                dateUtc.atTime(toLocalTime(sunriseHours)),
                dateUtc.atTime(toLocalTime(sunsetHours)),
                false,
                false
        );
    }

    public boolean isDaylight(SolarTimes solarTimes, LocalDateTime timeUtc) {
        if (solarTimes == null || timeUtc == null) {
            return false;
        }
        if (solarTimes.allDaylight()) {
            return true;
        }
        if (solarTimes.allNight() || solarTimes.sunriseUtc() == null || solarTimes.sunsetUtc() == null) {
            return false;
        }
        return !timeUtc.isBefore(solarTimes.sunriseUtc()) && timeUtc.isBefore(solarTimes.sunsetUtc());
    }

    private Double calculateUtcHours(LocalDate dateUtc, double latitude, double longitude, boolean sunrise) {
        int dayOfYear = dateUtc.getDayOfYear();
        double lngHour = longitude / 15.0;
        double approximateTime = dayOfYear + ((sunrise ? 6.0 : 18.0) - lngHour) / 24.0;
        double meanAnomaly = (0.9856 * approximateTime) - 3.289;

        double trueLongitude = meanAnomaly
                + (1.916 * Math.sin(Math.toRadians(meanAnomaly)))
                + (0.020 * Math.sin(Math.toRadians(2 * meanAnomaly)))
                + 282.634;
        trueLongitude = normalizeDegrees(trueLongitude);

        double rightAscension = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(trueLongitude))));
        rightAscension = normalizeDegrees(rightAscension);
        double longitudeQuadrant = Math.floor(trueLongitude / 90.0) * 90.0;
        double rightAscensionQuadrant = Math.floor(rightAscension / 90.0) * 90.0;
        rightAscension = (rightAscension + longitudeQuadrant - rightAscensionQuadrant) / 15.0;

        double sinDeclination = 0.39782 * Math.sin(Math.toRadians(trueLongitude));
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        double cosHourAngle = (Math.cos(Math.toRadians(ZENITH))
                - (sinDeclination * Math.sin(Math.toRadians(latitude))))
                / (cosDeclination * Math.cos(Math.toRadians(latitude)));

        if (cosHourAngle > 1 || cosHourAngle < -1) {
            return null;
        }

        double localHourAngle = sunrise
                ? 360.0 - Math.toDegrees(Math.acos(cosHourAngle))
                : Math.toDegrees(Math.acos(cosHourAngle));
        localHourAngle /= 15.0;

        double localMeanTime = localHourAngle + rightAscension - (0.06571 * approximateTime) - 6.622;
        double utcTime = localMeanTime - lngHour;
        while (utcTime < 0) {
            utcTime += 24.0;
        }
        while (utcTime >= 24) {
            utcTime -= 24.0;
        }
        return utcTime;
    }

    private double cosineLocalHourAngle(LocalDate dateUtc, double latitude, double longitude, boolean sunrise) {
        int dayOfYear = dateUtc.getDayOfYear();
        double lngHour = longitude / 15.0;
        double approximateTime = dayOfYear + ((sunrise ? 6.0 : 18.0) - lngHour) / 24.0;
        double meanAnomaly = (0.9856 * approximateTime) - 3.289;
        double trueLongitude = meanAnomaly
                + (1.916 * Math.sin(Math.toRadians(meanAnomaly)))
                + (0.020 * Math.sin(Math.toRadians(2 * meanAnomaly)))
                + 282.634;
        trueLongitude = normalizeDegrees(trueLongitude);

        double sinDeclination = 0.39782 * Math.sin(Math.toRadians(trueLongitude));
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        return (Math.cos(Math.toRadians(ZENITH))
                - (sinDeclination * Math.sin(Math.toRadians(latitude))))
                / (cosDeclination * Math.cos(Math.toRadians(latitude)));
    }

    private LocalTime toLocalTime(double utcHours) {
        int hours = (int) utcHours;
        int minutes = (int) ((utcHours - hours) * 60);
        int seconds = (int) Math.round((((utcHours - hours) * 60) - minutes) * 60);
        if (seconds == 60) {
            seconds = 0;
            minutes += 1;
        }
        if (minutes == 60) {
            minutes = 0;
            hours = (hours + 1) % 24;
        }
        return LocalTime.of(hours, minutes, seconds);
    }

    private double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0 ? normalized + 360.0 : normalized;
    }
}
