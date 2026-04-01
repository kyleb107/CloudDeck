/* Designed by: Kyle Barnes
   Collects METAR Weather Data, holds parsed METAR for a single airport
 */

public class MetarData {
    private String airportId;
    private String rawOb;
    private String observationTime;
    private int windDir;
    private int windSpeed;
    private float altimeter;
    private String flightCategory; //VFR, IFR, etc.

    public MetarData(String airportId, String rawOb, String observationTime, int windDir, int windSpeed, float altimeter, String flightCategory) {
        this.airportId = airportId;
        this.rawOb = rawOb;
        this.observationTime = observationTime;
        this.windDir = windDir;
        this.windSpeed = windSpeed;
        this.altimeter = altimeter;
        this.flightCategory = flightCategory;
    }

    //getters
    public String getAirportId() {return airportId; }
    public String getRawOb() {return rawOb; }
    public String getObservationTime() { return observationTime; }
    public int getWindDir() { return windDir; }
    public int getWindSpeed() { return windSpeed; }
    public float getAltimeter() { return altimeter; }
    public String getFlightCategory() { return flightCategory; }

    @Override
    public String toString() {
        return String.format(
                "Airport: %s | Time: %s | Wind: %03d at %d kts | Altimeter: %.2f | Category: %s",
                airportId, observationTime, windDir, windSpeed, altimeter, flightCategory
        );
    }
}