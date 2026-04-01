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

        //altimeter comes back as a float in inHg ex: 29.92
        float altimeter = json.optFloat("altim", 0.0f) / 33.8639f;

        String flightCategory = json.optString("fltCat", "UNKNOWN");

        return new MetarData(stationId, rawOb, observationTime, windDir, windSpeed, altimeter, flightCategory);
    }
}