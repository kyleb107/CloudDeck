package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AircraftProfile;
import com.kylebarnes.clouddeck.model.CrosswindComponents;
import com.kylebarnes.clouddeck.model.MetarData;
import com.kylebarnes.clouddeck.model.Runway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RunwayAnalysisService {

    public List<RunwayAnalysis> analyze(MetarData metar, List<Runway> runways) {
        return analyze(metar, runways, null);
    }

    public List<RunwayAnalysis> analyze(MetarData metar, List<Runway> runways, AircraftProfile aircraftProfile) {
        List<Runway> sortedRunways = new ArrayList<>(runways);
        sortedRunways.sort(Comparator.comparingDouble(
                runway -> Math.abs(CrosswindCalculator.calculate(runway.heading(), metar.windDir(), metar.windSpeed()).crosswindKts())
        ));

        List<RunwayAnalysis> analysis = new ArrayList<>();
        for (int index = 0; index < sortedRunways.size(); index++) {
            Runway runway = sortedRunways.get(index);
            CrosswindComponents components = CrosswindCalculator.calculate(runway.heading(), metar.windDir(), metar.windSpeed());
            boolean exceedsLimit = aircraftProfile != null
                    && Math.abs(components.crosswindKts()) > aircraftProfile.maxCrosswindKts();
            analysis.add(new RunwayAnalysis(runway, components, index == 0, exceedsLimit));
        }
        return analysis;
    }

    public String formatManualResult(CrosswindComponents components) {
        String headwindLabel = components.headwindKts() >= 0 ? "Headwind" : "Tailwind";
        String side = components.crosswindKts() >= 0 ? "from right" : "from left";
        return String.format(
                "%s: %.1f kts  |  Crosswind: %.1f kts %s",
                headwindLabel,
                Math.abs(components.headwindKts()),
                Math.abs(components.crosswindKts()),
                side
        );
    }
}
