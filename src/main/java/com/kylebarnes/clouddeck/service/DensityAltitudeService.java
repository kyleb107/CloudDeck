package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AirportInfo;
import com.kylebarnes.clouddeck.model.MetarData;

public class DensityAltitudeService {

    public DensityAltitudeAssessment assess(AirportInfo airportInfo, MetarData metar) {
        if (airportInfo == null || metar == null || airportInfo.elevationFt() < 0) {
            return null;
        }

        int pressureAltitude = (int) Math.round(
                airportInfo.elevationFt() + ((29.92 - metar.altimeterInHg()) * 1000.0)
        );
        double isaTemp = 15.0 - (2.0 * (pressureAltitude / 1000.0));
        int densityAltitude = (int) Math.round(
                pressureAltitude + (120.0 * (metar.tempC() - isaTemp))
        );

        if (densityAltitude >= 5000) {
            return new DensityAltitudeAssessment(
                    pressureAltitude,
                    densityAltitude,
                    VfrStatusLevel.WARNING,
                    "High density altitude. Expect degraded climb and takeoff performance."
            );
        }
        if (densityAltitude >= 3000) {
            return new DensityAltitudeAssessment(
                    pressureAltitude,
                    densityAltitude,
                    VfrStatusLevel.CAUTION,
                    "Elevated density altitude. Review takeoff and climb performance."
            );
        }

        return new DensityAltitudeAssessment(
                pressureAltitude,
                densityAltitude,
                VfrStatusLevel.VFR,
                "Density altitude is relatively benign."
        );
    }
}
