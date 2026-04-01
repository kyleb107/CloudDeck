/* Designed by: Kyle Barnes
   Data class — holds all parsed METAR fields for a single airport
 */

public class MetarData {

    //===== FIELDS =====
    private String airportId;
    private String rawOb;
    private String observationTime;
    private int windDir;
    private int windSpeed;
    private int windGust;
    private float altimeter;
    private String flightCategory;
    private String cloudLayers;
    private float tempC;
    private float visibSM;

    //===== CONSTRUCTOR =====
    public MetarData(String airportId, String rawOb, String observationTime,
                     int windDir, int windSpeed, int windGust,
                     float altimeter, String flightCategory, String cloudLayers,
                     float tempC, float visibSM) {
        this.airportId = airportId;
        this.rawOb = rawOb;
        this.observationTime = observationTime;
        this.windDir = windDir;
        this.windSpeed = windSpeed;
        this.windGust = windGust;
        this.altimeter = altimeter;
        this.flightCategory = flightCategory;
        this.cloudLayers = cloudLayers;
        this.tempC = tempC;
        this.visibSM = visibSM;
    }

    //===== GETTERS =====
    public String getAirportId() { return airportId; }
    public String getRawOb() { return rawOb; }
    public String getObservationTime() { return observationTime; }
    public int getWindDir() { return windDir; }
    public int getWindSpeed() { return windSpeed; }
    public int getWindGust() { return windGust; }
    public float getAltimeter() { return altimeter; }
    public String getFlightCategory() { return flightCategory; }
    public String getCloudLayers() { return cloudLayers; }
    public float getTempC() { return tempC; }
    public float getVisibSM() { return visibSM; }

    //===== TO STRING =====
    @Override
    public String toString() {
        String windString = windGust > 0
                ? String.format("%03d at %d kts gusting %d kts", windDir, windSpeed, windGust)
                : String.format("%03d at %d kts", windDir, windSpeed);

        return String.format(
                "Airport: %s | Time: %s | Wind: %s | Altimeter: %.2f | Category: %s | Clouds: %s",
                airportId, observationTime, windString, altimeter, flightCategory, cloudLayers
        );
    }
}