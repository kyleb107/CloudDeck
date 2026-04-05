package com.kylebarnes.clouddeck.model;

import java.nio.file.Path;

public record AirportDiagramPreview(
        String airportId,
        String pdfUrl,
        Path imagePath
) {
}
