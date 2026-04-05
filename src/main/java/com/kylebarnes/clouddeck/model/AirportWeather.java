package com.kylebarnes.clouddeck.model;

import java.util.List;

public record AirportWeather(AirportInfo airportInfo, MetarData metar, TafData taf, List<Runway> runways) {
}
