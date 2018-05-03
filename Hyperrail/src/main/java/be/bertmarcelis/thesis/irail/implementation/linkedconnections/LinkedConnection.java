package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */
class LinkedConnection {
    public static final String TZ_BRUSSELS = "Europe/Brussels";
    private final JSONObject mEntry;
    private String uri;
    private String departureStationUri;
    private String arrivalStationUri;
    private DateTime departureTime;
    private DateTime arrivalTime;
    private int departureDelay = -1;
    private int arrivalDelay = -1;
    private String direction;
    private String route;
    private String trip;
    private static final DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-ddd'T'HH:mm:ss.SSSZ");

    public LinkedConnection(JSONObject entry) {
        mEntry = entry;
    }

    DateTime getDelayedDepartureTime() {
        return getDepartureTime().plusSeconds(getDepartureDelay());
    }

    DateTime getDelayedArrivalTime() {
        return getArrivalTime().plusSeconds(getArrivalDelay());
    }

    public String getUri() {
        if (uri == null) {
            try {
                setUri(mEntry.getString("@id"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return uri;
    }

    protected void setUri(String uri) {
        this.uri = uri;
    }

    public String getDepartureStationUri() {
        if (departureStationUri == null) {
            try {
                setDepartureStationUri(mEntry.getString("departureStop"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return departureStationUri;
    }

    protected void setDepartureStationUri(String departureStationUri) {
        this.departureStationUri = departureStationUri;
    }

    public String getArrivalStationUri() {
        if (arrivalStationUri == null) {
            try {
                setArrivalStationUri(mEntry.getString("arrivalStop"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return arrivalStationUri;
    }

    protected void setArrivalStationUri(String arrivalStationUri) {
        this.arrivalStationUri = arrivalStationUri;
    }

    public DateTime getDepartureTime() {
        if (departureTime == null) {
            try {
                setDepartureTime(formatter.parseDateTime(mEntry.getString("departureTime")).withZone(DateTimeZone.forID(TZ_BRUSSELS)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return departureTime;
    }

    protected void setDepartureTime(DateTime departureTime) {
        this.departureTime = departureTime;
    }

    public DateTime getArrivalTime() {
        if (arrivalTime == null) {
            try {
                setArrivalTime(formatter.parseDateTime(mEntry.getString("arrivalTime")).withZone(DateTimeZone.forID(TZ_BRUSSELS)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return arrivalTime;
    }

    protected void setArrivalTime(DateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getDepartureDelay() {
        if (departureDelay == -1) {
            setDepartureDelay(0);
            if (mEntry.has("departureDelay")) {
                try {
                    setDepartureDelay(mEntry.getInt("departureDelay"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return departureDelay;
    }

    protected void setDepartureDelay(int departureDelay) {
        this.departureDelay = departureDelay;
    }

    public int getArrivalDelay() {
        if (arrivalDelay == -1) {
            setArrivalDelay(0);
            if (mEntry.has("arrivalDelay")) {
                try {
                    setArrivalDelay(mEntry.getInt("arrivalDelay"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return arrivalDelay;
    }

    protected void setArrivalDelay(int arrivalDelay) {
        this.arrivalDelay = arrivalDelay;
    }

    public String getDirection() {
        if (direction == null) {
            try {
                direction = mEntry.getString("direction");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return direction;
    }

    protected void setDirection(String direction) {
        this.direction = direction;
    }

    public String getRoute() {
        if (route == null) {
            try {
                route = mEntry.getString("gtfs:route");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return route;
    }

    protected void setRoute(String route) {
        this.route = route;
    }

    public String getTrip() {
        if (trip == null) {
            try {
                trip = mEntry.getString("gtfs:trip");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return trip;
    }

    protected void setTrip(String trip) {
        this.trip = trip;
    }
}