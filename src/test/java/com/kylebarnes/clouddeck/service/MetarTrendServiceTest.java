package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.MetarData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetarTrendServiceTest {
    private final MetarTrendService metarTrendService = new MetarTrendService();

    @Test
    void summarizeDetectsDeterioration() {
        List<MetarData> history = List.of(
                metar("2026-04-05T10:00:00Z", "VFR", 10.0f, List.of(new CloudLayer("BKN", 5000))),
                metar("2026-04-05T12:00:00Z", "MVFR", 5.0f, List.of(new CloudLayer("BKN", 2500))),
                metar("2026-04-05T14:00:00Z", "IFR", 2.0f, List.of(new CloudLayer("OVC", 900)))
        );

        MetarTrendSummary summary = metarTrendService.summarize(history, com.kylebarnes.clouddeck.model.AppSettings.defaults());

        assertEquals(VfrStatusLevel.WARNING, summary.level());
        assertTrue(summary.headline().contains("Deteriorating"));
        assertTrue(summary.categorySummary().contains("VFR -> IFR"));
    }

    private MetarData metar(String observationTime, String category, float visibilitySm, List<CloudLayer> clouds) {
        return new MetarData(
                "KAAA",
                "Alpha",
                "RAW",
                observationTime,
                180,
                10,
                0,
                29.95f,
                category,
                clouds,
                20.0f,
                visibilitySm
        );
    }
}
