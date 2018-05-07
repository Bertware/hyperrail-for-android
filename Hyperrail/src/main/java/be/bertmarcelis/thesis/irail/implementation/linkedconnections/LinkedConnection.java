package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.JsonAttribute;

import org.joda.time.DateTime;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */
@CompiledJson(onUnknown = CompiledJson.Behavior.IGNORE)
public class LinkedConnection {

    @JsonAttribute(name = "@id")
    protected String uri;
    @JsonAttribute(name = "departureStop")
    protected String departureStationUri;
    @JsonAttribute(name = "arrivalStop")
    protected String arrivalStationUri;
    @JsonAttribute(name = "departureTime")
    protected DateTime departureTime;
    @JsonAttribute(name = "arrivalTime")
    protected DateTime arrivalTime;
    @JsonAttribute(name = "departureDelay")
    protected int departureDelay = 0;
    @JsonAttribute(name = "arrivalDelay")
    protected int arrivalDelay = 0;
    @JsonAttribute(name = "direction")
    protected String direction;
    @JsonAttribute(name = "gtfs:route")
    protected String route;
    @JsonAttribute(name = "gtfs:trip")
    protected String trip;
    @JsonAttribute(name = "gtfs:pickupType")
    protected String pickupType;
    @JsonAttribute(name = "gtfs:dropOffType")
    protected String dropoffType;

    @CompiledJson(formats = {CompiledJson.Format.ARRAY, CompiledJson.Format.OBJECT})
    public LinkedConnection() {

    }

    DateTime getDelayedDepartureTime() {
        return getDepartureTime().plusSeconds(getDepartureDelay());
    }

    DateTime getDelayedArrivalTime() {
        return getArrivalTime().plusSeconds(getArrivalDelay());
    }

    public String getUri() {
        return uri;
    }

    public String getDepartureStationUri() {
        return departureStationUri;
    }

    public String getArrivalStationUri() {
        return arrivalStationUri;
    }

    public DateTime getDepartureTime() {
        return departureTime;
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

    public int getArrivalDelay() {
        return arrivalDelay;
    }

    public String getDirection() {
        return direction;
    }

    public String getRoute() {
        return route;
    }

    public String getTrip() {
        return trip;
    }

    protected void setTrip(String trip) {
        this.trip = trip;
    }

    public boolean isNormal(){
        return pickupType != null && dropoffType != null && pickupType.equals("gtfs:Regular") && dropoffType.equals("gtfs:Regular");
    }
}

