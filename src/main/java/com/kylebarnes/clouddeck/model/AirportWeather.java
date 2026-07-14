package com.kylebarnes.clouddeck.model;

import java.util.List;

public record AirportWeather(
        AirportInfo airportInfo,
        MetarData metar,
        TafData taf,
        List<Runway> runways,
        List<MetarData> metarHistory,
        List<Notam> notams,
        String tafStatusMessage
) {
    public AirportWeather(
            AirportInfo airportInfo,
            MetarData metar,
            TafData taf,
            List<Runway> runways,
            List<MetarData> metarHistory,
            String tafStatusMessage
    ) {
        this(airportInfo, metar, taf, runways, metarHistory, List.of(), tafStatusMessage);
    }
}
