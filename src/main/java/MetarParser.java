/* Designed by: Kyle Barnes
   Parses raw FAA JSON object into MetarData
 */
import org.json.JSONObject;

public class MetarParser {

    public MetarData parse(JSONObject json) {
        String stationId = json.optString("icaoId", "UNKNOWN");
        String rawOb = json.optString("rawOb", "");
        String observationTime = json.optString("reportTime", "");
        int windDir = json.optInt("wdir", 0);
        int windSpeed = json.optInt("wspd", 0);
        int windGust = json.optInt("wgst", 0);

        //altimeter comes back as a float in inHg ex: 29.92
        float altimeter = json.optFloat("altim", 0.0f) / 33.8639f;

        String flightCategory = json.optString("fltCat", "UNKNOWN");

        //parse cloud layers
        String cloudLayers = "Clear";
        if (json.has("clouds")) {
            org.json.JSONArray clouds = json.getJSONArray("clouds");
            if (!clouds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < clouds.length(); i++) {
                    org.json.JSONObject layer = clouds.getJSONObject(i);
                    String cover = layer.optString("cover", "");
                    int base = layer.optInt("base", 0);
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(cover).append(" at ").append(base).append("ft");
                }
                cloudLayers = sb.toString();
            }
        }

        return new MetarData(stationId, rawOb, observationTime, windDir, windSpeed, windGust, altimeter, flightCategory, cloudLayers);
    }
}