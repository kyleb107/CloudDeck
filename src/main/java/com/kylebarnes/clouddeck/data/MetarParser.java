package com.kylebarnes.clouddeck.data;

import com.kylebarnes.clouddeck.model.CloudLayer;
import com.kylebarnes.clouddeck.model.MetarData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MetarParser {

    public MetarData parse(JSONObject json) {
        String stationId = json.optString("icaoId", "UNKNOWN");
        String airportName = json.optString("name", "");
        String rawObservation = json.optString("rawOb", "");
        String observationTime = json.optString("reportTime", "");
        String flightCategory = json.optString("fltCat", "UNKNOWN");
        int windDir = json.optInt("wdir", 0);
        int windSpeed = json.optInt("wspd", 0);
        int windGust = json.optInt("wgst", 0);
        float altimeter = json.optFloat("altim", 0.0f) / 33.8639f;
        float tempC = json.optFloat("temp", 0.0f);

        float visibilitySm = 0.0f;
        try {
            String visibility = json.optString("visib", "0");
            visibilitySm = Float.parseFloat(visibility.replace("+", "").trim());
        } catch (NumberFormatException ignored) {
        }

        List<CloudLayer> cloudLayers = new ArrayList<>();
        if (json.has("clouds")) {
            JSONArray clouds = json.getJSONArray("clouds");
            for (int index = 0; index < clouds.length(); index++) {
                JSONObject layer = clouds.getJSONObject(index);
                String cover = layer.optString("cover", "").trim();
                int base = layer.optInt("base", 0);
                if (!cover.isEmpty()) {
                    cloudLayers.add(new CloudLayer(cover, base));
                }
            }
        }

        return new MetarData(
                stationId,
                airportName,
                rawObservation,
                observationTime,
                windDir,
                windSpeed,
                windGust,
                altimeter,
                flightCategory,
                List.copyOf(cloudLayers),
                tempC,
                visibilitySm
        );
    }
}
