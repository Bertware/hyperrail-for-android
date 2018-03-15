package be.hyperrail.android.irail.implementation.linkedconnections;

import org.joda.time.DateTime;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */
class LinkedConnection {
    String uri;
    String departureStationUri, arrivalStationUri;
    DateTime departureTime, arrivalTime;
    int departureDelay, arrivalDelay;
    String direction;
    String route, trip;

    DateTime getDelayedDepartureTime() {
        return departureTime.plusSeconds(departureDelay);
    }

    DateTime getDelayedArrivalTime() {
        return arrivalTime.plusSeconds(arrivalDelay);
    }
}

