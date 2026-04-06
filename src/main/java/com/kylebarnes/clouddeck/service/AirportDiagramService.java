package com.kylebarnes.clouddeck.service;

import com.kylebarnes.clouddeck.model.AirportDiagramPreview;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AirportDiagramService {
    private final HttpClient httpClient;
    private final FaaChartLinkService faaChartLinkService;
    private volatile Map<String, String> pdfNameByAirport = Map.of();

    public AirportDiagramService() {
        this(HttpClient.newHttpClient(), new FaaChartLinkService());
    }

    AirportDiagramService(HttpClient httpClient, FaaChartLinkService faaChartLinkService) {
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.faaChartLinkService = faaChartLinkService == null ? new FaaChartLinkService() : faaChartLinkService;
    }

    public AirportDiagramPreview loadPreview(String airportId) throws Exception {
        String normalizedAirportId = normalizeAirportId(airportId);
        if (normalizedAirportId.isBlank()) {
            return null;
        }

        String pdfName = ensurePdfLookup().get(normalizedAirportId);
        if (pdfName == null) {
            pdfName = ensurePdfLookup().get(faaChartLinkService.faaSearchId(normalizedAirportId));
        }
        if (pdfName == null) {
            return null;
        }

        Path pdfPath = ensurePdfCached(pdfName);
        Path imagePath = ensurePreviewRendered(normalizedAirportId, pdfPath);
        return new AirportDiagramPreview(normalizedAirportId, faaChartLinkService.buildPdfUrl(pdfName), imagePath);
    }

    private synchronized Map<String, String> ensurePdfLookup() throws Exception {
        if (!pdfNameByAirport.isEmpty()) {
            return pdfNameByAirport;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(faaChartLinkService.buildDtppXmlUrl()))
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("FAA diagram index returned status " + response.statusCode());
        }

        Document document;
        try (InputStream inputStream = response.body()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(inputStream);
        }

        Map<String, String> lookup = new HashMap<>();
        NodeList airportNodes = document.getElementsByTagName("airport_name");
        for (int index = 0; index < airportNodes.getLength(); index++) {
            Node airportNode = airportNodes.item(index);
            if (!(airportNode instanceof Element airportElement)) {
                continue;
            }

            String aptIdent = normalizeAirportId(airportElement.getAttribute("apt_ident"));
            String icaoIdent = normalizeAirportId(airportElement.getAttribute("icao_ident"));
            NodeList children = airportElement.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (!(child instanceof Element record) || !"record".equals(record.getTagName())) {
                    continue;
                }

                String chartCode = textContent(record, "chart_code");
                String pdfName = textContent(record, "pdf_name");
                if (!"APD".equalsIgnoreCase(chartCode) || pdfName.isBlank()) {
                    continue;
                }

                if (!icaoIdent.isBlank()) {
                    lookup.put(icaoIdent, pdfName);
                }
                if (!aptIdent.isBlank()) {
                    lookup.put(aptIdent, pdfName);
                }
                break;
            }
        }

        pdfNameByAirport = Map.copyOf(lookup);
        return pdfNameByAirport;
    }

    private Path ensurePdfCached(String pdfName) throws Exception {
        Path cycleDirectory = cacheDirectory().resolve(faaChartLinkService.currentAiracCycle());
        Files.createDirectories(cycleDirectory);

        Path pdfPath = cycleDirectory.resolve(pdfName);
        if (Files.exists(pdfPath) && Files.size(pdfPath) > 0) {
            return pdfPath;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(faaChartLinkService.buildPdfUrl(pdfName)))
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(pdfPath));
        if (response.statusCode() >= 400) {
            Files.deleteIfExists(pdfPath);
            throw new IOException("Failed to download PDF, status " + response.statusCode());
        }
        return pdfPath;
    }

    private Path ensurePreviewRendered(String airportId, Path pdfPath) throws IOException {
        Path cycleDirectory = cacheDirectory().resolve(faaChartLinkService.currentAiracCycle());
        Files.createDirectories(cycleDirectory);

        Path imagePath = cycleDirectory.resolve(airportId + "-diagram.png");
        if (Files.exists(imagePath) && Files.size(imagePath) > 0) {
            return imagePath;
        }

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 110);
            ImageIO.write(image, "png", imagePath.toFile());
        }
        return imagePath;
    }

    private String textContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String normalizeAirportId(String airportId) {
        return airportId == null ? "" : airportId.trim().toUpperCase();
    }

    private Path cacheDirectory() {
        return Path.of(System.getProperty("user.home"), ".clouddeck", "charts");
    }
}