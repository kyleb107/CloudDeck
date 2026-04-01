/* Designed by: Kyle Barnes
   Parses a raw FAA JSON object into a MetarData instance
 */

import org.json.JSONArray;
import org.json.JSONObject;

public class MetarParser {

    public MetarData parse(JSONObject json) {

        //===== BASIC FIELDS =====
        String stationId = json.optString("icaoId", "UNKNOWN");
        String airportName = json.optString("name", "");
        String rawOb = json.optString("rawOb", "");
        String observationTime = json.optString("reportTime", "");
        String flightCategory = json.optString("fltCat", "UNKNOWN");

        //===== WIND =====
        int windDir = json.optInt("wdir", 0);
        int windSpeed = json.optInt("wspd", 0);
        int windGust = json.optInt("wgst", 0);

        //===== ALTIMETER =====
        //API returns hPa so convert to inHg ex: 1013.25 hPa = 29.92 inHg
        float altimeter = json.optFloat("altim", 0.0f) / 33.8639f;

        //===== TEMPERATURE =====
        float tempC = json.optFloat("temp", 0.0f);

        //===== VISIBILITY =====
        //visibility comes back as a string ex: "10+" so strip the + before parsing
        float visibSM = 0.0f;
        try {
            String visibStr = json.optString("visib", "0");
            visibSM = Float.parseFloat(visibStr.replace("+", "").trim());
        }
        catch (NumberFormatException ignored) {}

        //===== CLOUD LAYERS =====
        //build a readable string from the clouds array ex: "SCT at 4500ft, BKN at 25000ft"
        String cloudLayers = "Clear";
        if (json.has("clouds")) {
            JSONArray clouds = json.getJSONArray("clouds");
            if (!clouds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < clouds.length(); i++) {
                    JSONObject layer = clouds.getJSONObject(i);
                    String cover = layer.optString("cover", "");
                    int base = layer.optInt("base", 0);
                    if (i > 0) sb.append(", ");
                    sb.append(cover).append(" at ").append(base).append("ft");
                }
                cloudLayers = sb.toString();
            }
        }

        //===== RETURN =====
        return new MetarData(stationId, airportName, rawOb, observationTime,
                windDir, windSpeed, windGust,
                altimeter, flightCategory, cloudLayers,
                tempC, visibSM);
    }
}