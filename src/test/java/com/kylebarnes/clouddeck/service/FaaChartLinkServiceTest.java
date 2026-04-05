package com.kylebarnes.clouddeck.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaaChartLinkServiceTest {
    private final FaaChartLinkService service = new FaaChartLinkService();

    @Test
    void currentAiracCycleAdvancesFromKnownBaseline() {
        assertEquals("2413", service.currentAiracCycle(LocalDate.of(2024, 12, 26)));
        assertEquals("2501", service.currentAiracCycle(LocalDate.of(2025, 1, 23)));
        assertEquals("2603", service.currentAiracCycle(LocalDate.of(2026, 4, 5)));
    }

    @Test
    void faaSearchIdDropsCommonUsIcaoPrefix() {
        assertEquals("DFW", service.faaSearchId("KDFW"));
        assertEquals("ANC", service.faaSearchId("PANC"));
        assertEquals("D69", service.faaSearchId("D69"));
    }

    @Test
    void generatedUrlsIncludeCycleAndAirportIdentifier() {
        assertTrue(service.buildAirportDiagramUrl("KDFW").contains("ident=DFW"));
        assertTrue(service.buildChartSupplementUrl("KDFW").contains("ident=DFW"));
    }
}
