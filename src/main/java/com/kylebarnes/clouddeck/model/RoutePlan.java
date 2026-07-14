package com.kylebarnes.clouddeck.model;

public record RoutePlan(
        AirportInfo departureAirport,
        AirportInfo destinationAirport,
        double distanceNm,
        double trueCourse,
        double trueHeading,
        double windCorrectionAngle,
        double groundspeedKts,
        double estimatedTimeHours,
        double airborneFuelGallons,
        double taxiFuelGallons,
        double climbFuelGallons,
        double tripFuelGallons,
        double reserveRemainingGallons,
        boolean reserveSatisfied
) {
    public RoutePlan(
            AirportInfo departureAirport,
            AirportInfo destinationAirport,
            double distanceNm,
            double groundspeedKts,
            double estimatedTimeHours,
            double airborneFuelGallons,
            double taxiFuelGallons,
            double climbFuelGallons,
            double tripFuelGallons,
            double reserveRemainingGallons,
            boolean reserveSatisfied
    ) {
        this(
                departureAirport,
                destinationAirport,
                distanceNm,
                0.0,
                0.0,
                0.0,
                groundspeedKts,
                estimatedTimeHours,
                airborneFuelGallons,
                taxiFuelGallons,
                climbFuelGallons,
                tripFuelGallons,
                reserveRemainingGallons,
                reserveSatisfied
        );
    }
}
