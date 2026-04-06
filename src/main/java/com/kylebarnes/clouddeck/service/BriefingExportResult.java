package com.kylebarnes.clouddeck.service;

import java.nio.file.Path;

public record BriefingExportResult(
        Path textPath,
        Path pdfPath
) {
}
