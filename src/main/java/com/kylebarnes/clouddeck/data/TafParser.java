package com.kylebarnes.clouddeck.data;

import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.TafData;
import com.kylebarnes.clouddeck.model.TafPeriod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TafParser {
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^TAF(?:\\s+(AMD|COR))?\\s+([A-Z0-9]{4})\\s+(\\d{6}Z)\\s+(\\d{4}/\\d{4})\\s+(.*)$"
    );
    private static final Pattern FM_PATTERN = Pattern.compile("^FM(\\d{6})$");
    private static final Pattern CLOUD_PATTERN = Pattern.compile("^(FEW|SCT|BKN|OVC|VV)(\\d{3}).*$");
    private static final Pattern WIND_PATTERN = Pattern.compile("^(\\d{3}|VRB)(\\d{2,3})(G(\\d{2,3}))?KT$");

    public Map<String, TafData> parseMany(String responseBody) {
        Map<String, TafData> tafsByAirport = new LinkedHashMap<>();
        if (responseBody == null || responseBody.isBlank()) {
            return tafsByAirport;
        }

        String normalized = responseBody.replace("\r", "").trim();
        String[] chunks = normalized.split("(?=TAF(?:\\s+(?:AMD|COR))?\\s+[A-Z0-9]{4}\\s+)");
        for (String chunk : chunks) {
            TafData taf = parseSingle(chunk.trim());
            if (taf != null) {
                tafsByAirport.put(taf.airportId(), taf);
            }
        }
        return tafsByAirport;
    }

    private TafData parseSingle(String rawTaf) {
        if (rawTaf.isBlank()) {
            return null;
        }

        String normalized = rawTaf.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        Matcher headerMatcher = HEADER_PATTERN.matcher(normalized);
        if (!headerMatcher.matches()) {
            return null;
        }

        String amendment = headerMatcher.group(1);
        String airportId = headerMatcher.group(2);
        String issueTime = headerMatcher.group(3);
        String validPeriod = headerMatcher.group(4);
        String remainder = headerMatcher.group(5);

        List<TafPeriod> periods = parsePeriods(remainder, validPeriod);
        String normalizedRawText = amendment == null
                ? normalized
                : normalized.replaceFirst("^TAF\\s+", "TAF " + amendment + " ");

        return new TafData(airportId, issueTime, validPeriod, normalizedRawText, List.copyOf(periods));
    }

    private List<TafPeriod> parsePeriods(String remainder, String validPeriod) {
        List<String> tokens = List.of(remainder.split("\\s+"));
        List<List<String>> rawSegments = new ArrayList<>();

        List<String> currentSegment = new ArrayList<>();
        currentSegment.add("BASE");
        currentSegment.add(validPeriod);

        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);

            if (FM_PATTERN.matcher(token).matches()) {
                rawSegments.add(currentSegment);
                currentSegment = new ArrayList<>();
                currentSegment.add("FM");
                currentSegment.add(token.substring(2));
                continue;
            }

            if ("TEMPO".equals(token) || "BECMG".equals(token) || token.startsWith("PROB30") || token.startsWith("PROB40")) {
                rawSegments.add(currentSegment);
                currentSegment = new ArrayList<>();
                currentSegment.add(token);

                if ((token.startsWith("PROB30") || token.startsWith("PROB40"))
                        && index + 1 < tokens.size()
                        && "TEMPO".equals(tokens.get(index + 1))) {
                    currentSegment.add(tokens.get(++index));
                }

                if (index + 1 < tokens.size() && tokens.get(index + 1).matches("\\d{4}/\\d{4}")) {
                    currentSegment.add(tokens.get(++index));
                }
                continue;
            }

            currentSegment.add(token);
        }
        rawSegments.add(currentSegment);

        List<TafPeriod> periods = new ArrayList<>();
        for (List<String> segment : rawSegments) {
            TafPeriod period = parsePeriod(segment);
            if (period != null) {
                periods.add(period);
            }
        }
        return periods;
    }

    private TafPeriod parsePeriod(List<String> segment) {
        if (segment.size() < 2) {
            return null;
        }

        int bodyStartIndex = 2;
        String type = segment.get(0);
        String label = switch (type) {
            case "BASE" -> "Initial " + segment.get(1);
            case "FM" -> "FM " + formatCompactTime(segment.get(1));
            default -> segment.get(0) + " " + (segment.size() > 1 ? segment.get(1) : "");
        };

        if (!"BASE".equals(type) && !"FM".equals(type) && segment.size() > 2 && "TEMPO".equals(segment.get(1))) {
            label = segment.get(0) + " TEMPO " + segment.get(2);
            bodyStartIndex = 3;
        } else if (!"BASE".equals(type) && !"FM".equals(type)) {
            bodyStartIndex = segment.size() > 1 && segment.get(1).matches("\\d{4}/\\d{4}") ? 2 : 1;
        }

        Integer windDir = null;
        int windSpeed = 0;
        int windGust = 0;
        Float visibilitySm = null;
        List<CloudLayer> cloudLayers = new ArrayList<>();
        List<String> weatherTokens = new ArrayList<>();

        for (int index = bodyStartIndex; index < segment.size(); index++) {
            String token = segment.get(index);
            Matcher windMatcher = WIND_PATTERN.matcher(token);
            Matcher cloudMatcher = CLOUD_PATTERN.matcher(token);

            if (windMatcher.matches()) {
                windDir = "VRB".equals(windMatcher.group(1)) ? null : Integer.parseInt(windMatcher.group(1));
                windSpeed = Integer.parseInt(windMatcher.group(2));
                if (windMatcher.group(4) != null) {
                    windGust = Integer.parseInt(windMatcher.group(4));
                }
                continue;
            }

            if (cloudMatcher.matches()) {
                cloudLayers.add(new CloudLayer(
                        cloudMatcher.group(1).toUpperCase(Locale.US),
                        Integer.parseInt(cloudMatcher.group(2)) * 100
                ));
                continue;
            }

            ParsedVisibility parsedVisibility = parseVisibility(segment, index);
            if (parsedVisibility != null) {
                visibilitySm = parsedVisibility.visibilitySm();
                index += parsedVisibility.tokensConsumed() - 1;
                continue;
            }

            if (looksLikeWeatherToken(token)) {
                weatherTokens.add(token);
            }
        }

        String rawText = String.join(" ", segment.subList(bodyStartIndex, segment.size()));
        return new TafPeriod(
                label.trim(),
                type,
                windDir,
                windSpeed,
                windGust,
                visibilitySm,
                List.copyOf(cloudLayers),
                List.copyOf(weatherTokens),
                rawText
        );
    }

    private ParsedVisibility parseVisibility(List<String> tokens, int index) {
        String token = tokens.get(index);
        if (token.endsWith("SM")) {
            Float visibility = parseVisibilityValue(token);
            if (visibility != null) {
                return new ParsedVisibility(visibility, 1);
            }
        }

        if (index + 1 < tokens.size() && tokens.get(index + 1).endsWith("SM")) {
            String combinedToken = token + " " + tokens.get(index + 1);
            Float visibility = parseVisibilityValue(combinedToken);
            if (visibility != null) {
                return new ParsedVisibility(visibility, 2);
            }
        }

        return null;
    }

    private Float parseVisibilityValue(String token) {
        String normalized = token.replace("SM", "").trim();
        if ("P6".equals(normalized)) {
            return 6.0f;
        }

        try {
            if (normalized.contains(" ")) {
                String[] parts = normalized.split("\\s+");
                float whole = Float.parseFloat(parts[0]);
                return whole + parseFraction(parts[1]);
            }
            if (normalized.contains("/")) {
                return parseFraction(normalized);
            }
            return Float.parseFloat(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private float parseFraction(String value) {
        String[] pieces = value.split("/");
        if (pieces.length != 2) {
            throw new NumberFormatException("Invalid fraction: " + value);
        }
        return Float.parseFloat(pieces[0]) / Float.parseFloat(pieces[1]);
    }

    private boolean looksLikeWeatherToken(String token) {
        return token.matches("^[+-]?[A-Z]{2,}$");
    }

    private String formatCompactTime(String compactTime) {
        if (compactTime.length() != 6) {
            return compactTime;
        }
        return compactTime.substring(0, 2) + "/" + compactTime.substring(2, 4) + compactTime.substring(4) + "Z";
    }

    private record ParsedVisibility(float visibilitySm, int tokensConsumed) {
    }
}
