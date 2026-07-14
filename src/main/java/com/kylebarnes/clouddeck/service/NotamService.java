package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.Notam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotamService {
    public Map<String, List<Notam>> fetchNotams(List<String> airportIds) {
        Map<String, List<Notam>> notamsByAirport = new HashMap<>();
        for (String airportId : airportIds) {
            notamsByAirport.put(airportId, List.of());
        }
        return notamsByAirport;
    }
}
