/* Designed by: Kyle Barnes
   Data class representing a single runway with its identifier and heading
 */

public class Runway {

    //===== FIELDS =====
    private String ident;   //ex: 17L, 35R
    private int heading;    //ex: 170, 350

    //===== CONSTRUCTOR =====
    public Runway(String ident, int heading) {
        this.ident = ident;
        this.heading = heading;
    }

    //===== GETTERS =====
    public String getIdent() { return ident; }
    public int getHeading() { return heading; }

    //===== TO STRING =====
    @Override
    public String toString() { return ident; }
}