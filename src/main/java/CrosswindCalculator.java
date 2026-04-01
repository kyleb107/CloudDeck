/* Designed by: Kyle Barnes
   Calculates headwind and crosswind components from METAR wind data
 */

public class CrosswindCalculator {

    //===== CALCULATE =====
    //returns double[0] = headwind, double[1] = crosswind
    //negative headwind = tailwind, negative crosswind = from the left
    public static double[] calculate(int runwayHeading, int windDir, int windSpeed) {
        double angleDiff = Math.toRadians(windDir - runwayHeading);
        double headwind = windSpeed * Math.cos(angleDiff);
        double crosswind = windSpeed * Math.sin(angleDiff);
        return new double[]{headwind, crosswind};
    }

    //===== PRINT (terminal fallback) =====
    //used by Main.java terminal interface — not used by GUI
    public static void printComponents(int runwayHeading, int windDir, int windSpeed) {
        double[] components = calculate(runwayHeading, windDir, windSpeed);
        double headwind = components[0];
        double crosswind = components[1];

        String headwindLabel = headwind >= 0 ? "Headwind" : "Tailwind";
        String crosswindLabel = crosswind >= 0 ? "From the right" : "From the left";

        System.out.printf("Runway %02d | %s: %.1f kts | Crosswind: %.1f kts (%s)%n",
                runwayHeading / 10, headwindLabel,
                Math.abs(headwind), Math.abs(crosswind), crosswindLabel);
    }
}