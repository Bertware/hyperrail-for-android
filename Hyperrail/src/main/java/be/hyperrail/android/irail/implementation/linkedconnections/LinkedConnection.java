package be.hyperrail.android.irail.implementation.linkedconnections;

import org.joda.time.DateTime;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */
class LinkedConnection {
    private String uri;
    private String departureStationUri;
    private String arrivalStationUri;
    private DateTime departureTime;
    private DateTime arrivalTime;
    private int departureDelay;
    private int arrivalDelay;
    private String direction;
    private String route;
    private String trip;

    DateTime getDelayedDepartureTime() {
        return getDepartureTime().plusSeconds(getDepartureDelay());
    }

    DateTime getDelayedArrivalTime() {
        return getArrivalTime().plusSeconds(getArrivalDelay());
    }

    public String getUri() {
        return uri;
    }

    protected void setUri(String uri) {
        this.uri = uri;
    }

    public String getDepartureStationUri() {
        return departureStationUri;
    }

    protected void setDepartureStationUri(String departureStationUri) {
        this.departureStationUri = departureStationUri;
    }

    public String getArrivalStationUri() {
        return arrivalStationUri;
    }

    protected void setArrivalStationUri(String arrivalStationUri) {
        this.arrivalStationUri = arrivalStationUri;
    }

    public DateTime getDepartureTime() {
        return departureTime;
    }

    protected void setDepartureTime(DateTime departureTime) {
        this.departureTime = departureTime;
    }

    public DateTime getArrivalTime() {
        return arrivalTime;
    }

    protected void setArrivalTime(DateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getDepartureDelay() {
        return departureDelay;
    }

    protected void setDepartureDelay(int departureDelay) {
        this.departureDelay = departureDelay;
    }

    public int getArrivalDelay() {
        return arrivalDelay;
    }

    protected void setArrivalDelay(int arrivalDelay) {
        this.arrivalDelay = arrivalDelay;
    }

    public String getDirection() {
        return direction;
    }

    protected void setDirection(String direction) {
        this.direction = direction;
    }

    public String getRoute() {
        return route;
    }

    protected void setRoute(String route) {
        this.route = route;
    }

    public String getTrip() {
        return trip;
    }

    protected void setTrip(String trip) {
        this.trip = trip;
    }
}

