/* Designed by: Kyle Barnes
   Calculates headwind and crosswind components from METAR wind data
 */

public class CrosswindCalculator {

    //takes runway heading & wind direction/speed from METAR
    public static double[] calculate(int runwayHeading, int windDir, int windSpeed) {
        //convert angle difference to radians
        double angleDiff = Math.toRadians(windDir - runwayHeading);

        //calculate headwind & crosswind
        double headwind = windSpeed * Math.cos(angleDiff);
        double crosswind = windSpeed * Math.sin(angleDiff);

        //negative headwind = tailwind, negative crosswind = from the left
        return new double[]{headwind, crosswind};
    }

    public static void printComponents(int runwayHeading, int windDir, int windSpeed) {
        double[] components = calculate(runwayHeading, windDir, windSpeed);
        double headwind = components[0];
        double crosswind = components[1];

        //determine if headwind/tailwind or if crosswind from right/left
        String headwindLabel = headwind >= 0 ? "Headwind" : "Tailwind";
        String crosswindLabel = crosswind >= 0 ? "From the right" : "From the left";

        //output calculated result
        System.out.printf("Runway %02d | %s: %.1f kts | Crosswind: %1f kts (%s)%n", runwayHeading / 10, headwindLabel, Math.abs(headwind), Math.abs(crosswind), crosswindLabel);
    }
}
