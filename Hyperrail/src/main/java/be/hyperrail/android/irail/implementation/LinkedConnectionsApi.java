package be.hyperrail.android.irail.implementation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import be.hyperrail.android.BuildConfig;
import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.OccupancyLevel;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.LinkedConnections.LinkedConnectionsOfflineCache;
import be.hyperrail.android.irail.implementation.requests.IrailDisturbanceRequest;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.IrailPostOccupancyRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRouteRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailVehicleRequest;

/**
 * This API loads linkedConnection data and builds responses based on this data
 */
public class LinkedConnectionsApi implements IrailDataProvider {

    private Context mContext;
    private static final String LOGTAG = "LinkedConnectionsApi";
    private final RequestQueue requestQueue;
    private static final String UA = "HyperRail for Android - " + BuildConfig.VERSION_NAME;
    private final RetryPolicy requestPolicy;
    private LinkedConnectionsOfflineCache mLinkedConnectionsOfflineCache;

    public LinkedConnectionsApi(Context context) {
        this.mContext = context;
        this.mLinkedConnectionsOfflineCache = new LinkedConnectionsOfflineCache(context);
        this.requestQueue = Volley.newRequestQueue(context);
        this.requestPolicy = new DefaultRetryPolicy(
                1000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );
    }

    @Override
    public void getDisturbances(@NonNull IrailDisturbanceRequest... request) {
        // Fallback to the legacy API
        IrailApi api = new IrailApi(mContext);
        api.getDisturbances(request);
    }

    @Override
    public void getLiveboard(@NonNull IrailLiveboardRequest... requests) {
        for (IrailLiveboardRequest request :
                requests) {
            getLiveboard(request);
        }
    }

    private void getLiveboard(@NonNull final IrailLiveboardRequest request) {
        LiveboardResponseListener listener = new LiveboardResponseListener(request);
        getLinkedConnectionsByDate(request.getSearchTime(),
                                   listener,
                                   listener,
                                   request.getTag());
    }

    @Override
    public void getLiveboardBefore(@NonNull IrailLiveboardRequest... request) {

    }

    @Override
    public void getRoutes(@NonNull IrailRoutesRequest... request) {

    }

    @Override
    public void getRoute(@NonNull IrailRouteRequest... request) {

    }

    @Override
    public void getTrain(@NonNull IrailVehicleRequest... requests) {
        for (IrailVehicleRequest request :
                requests) {
            getTrain(request);
        }
    }

    public void getTrain(@NonNull final IrailVehicleRequest request) {
        Log.i(LOGTAG, "Loading train...");
        VehicleResponseListener listener = new VehicleResponseListener(request);
        getLinkedConnectionsByDateForTimeSpan(request.getSearchTime().withTimeAtStartOfDay().withHourOfDay(3), request.getSearchTime().withTimeAtStartOfDay().plusDays(1).withHourOfDay(3), listener, listener, null);
    }

    @Override
    public void postOccupancy(@NonNull IrailPostOccupancyRequest... request) {
        // Fallback to the legacy API
        IrailApi api = new IrailApi(mContext);
        api.postOccupancy(request);
    }

    @Override
    public void abortAllQueries() {

    }

    private void getLinkedConnectionsByDate(DateTime startTime, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {
        startTime = startTime.withMillisOfSecond(0);
        startTime = startTime.withSecondOfMinute(0);
        startTime = startTime.minusMinutes(startTime.getMinuteOfHour() % 10);

        String url = "https://graph.irail.be/sncb/connections?departureTime=" +
                startTime.toString(ISODateTimeFormat.dateTime());

        getLinkedConnectionByUrl(url, successListener, errorListener, tag);
    }

    private void getLinkedConnectionsByDateForTimeSpan(DateTime startTime, DateTime endTime, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {

        final int calls = (int) (new Duration(startTime, endTime).getStandardMinutes() / 10);
        final LinkedConnections[] results = new LinkedConnections[calls];
        final int[] threadStatus = new int[calls];

        int i = 0;
        for (DateTime current = new DateTime(startTime); current.isBefore(endTime); current = current.plusMinutes(10)) {
            final int id = i;
            threadStatus[i] = 1;
            getLinkedConnectionsByDate(current, new IRailSuccessResponseListener<LinkedConnections>() {
                @Override
                public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {
                    results[id] = data;
                    Log.d(LOGTAG, "Compiling larger page, received " + data.current + " STORE " + id);
                    List<LinkedConnection> connections = new ArrayList<>();
                    LinkedConnections resultPage = new LinkedConnections();

                    for (int j = 0; j < calls; j++) {
                        if (j != id && threadStatus[j] != 2) {
                            threadStatus[id] = 2;
                            return;
                        }
                        connections.addAll(Arrays.asList(results[j].connections));
                    }
                    threadStatus[id] = 2;

                    LinkedConnection[] linkedConnections = new LinkedConnection[connections.size()];
                    resultPage.connections = connections.toArray(linkedConnections);

                    Arrays.sort(resultPage.connections, new Comparator<LinkedConnection>() {
                        @Override
                        public int compare(LinkedConnection o1, LinkedConnection o2) {
                            return o1.departureTime.compareTo(o2.departureTime);
                        }
                    });

                    resultPage.previous = results[0].previous;
                    resultPage.current = results[0].current;
                    resultPage.next = results[calls - 1].next;
                    successListener.onSuccessResponse(resultPage, tag);
                }
            }, new IRailErrorResponseListener() {
                @Override
                public void onErrorResponse(@NonNull Exception e, Object tag) {
                    results[id] = new LinkedConnections();
                    Log.d(LOGTAG, "Compiling larger page, failed to receive STORE " + id);
                    List<LinkedConnection> connections = new ArrayList<>();
                    LinkedConnections resultPage = new LinkedConnections();

                    for (int j = 0; j < calls; j++) {
                        if (j != id && threadStatus[j] != 2) {
                            threadStatus[id] = 2;
                            return;
                        }
                        connections.addAll(Arrays.asList(results[j].connections));
                    }
                    threadStatus[id] = 2;

                    LinkedConnection[] linkedConnections = new LinkedConnection[connections.size()];
                    resultPage.connections = connections.toArray(linkedConnections);

                    Arrays.sort(resultPage.connections, new Comparator<LinkedConnection>() {
                        @Override
                        public int compare(LinkedConnection o1, LinkedConnection o2) {
                            return o1.departureTime.compareTo(o2.departureTime);
                        }
                    });

                    resultPage.previous = results[0].previous;
                    resultPage.current = results[0].current;
                    resultPage.next = results[calls - 1].next;
                    successListener.onSuccessResponse(resultPage, tag);
                }
            }, tag);
            i++;
        }
    }


    private void getLinkedConnectionByUrl(final String url, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, final Object tag) {
        // https://graph.irail.be/sncb/connections?departureTime={ISO8601}
        // Log.i(LOGTAG, "Loading " + url);
        // TODO: prevent loading the same URL twice when two requests are made short after each other (locking based on URL)

        Response.Listener<JSONObject> volleySuccessListener = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.w(LOGTAG, "Getting LC page successful");
                try {
                    LinkedConnections result = getLinkedConnectionsFromJson(response);
                    mLinkedConnectionsOfflineCache.store(url, response.toString());
                    successListener.onSuccessResponse(result, tag);
                } catch (JSONException e) {
                    e.printStackTrace();
                    errorListener.onErrorResponse(e, tag);
                }

            }
        };

        Response.ErrorListener volleyErrorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w(LOGTAG, "Getting LC page " + url + " failed: " + error.getMessage());
                LinkedConnectionsOfflineCache.CachedLinkedConnections cache = mLinkedConnectionsOfflineCache.load(url);
                if (cache == null) {
                    Log.w(LOGTAG, "Getting LC page " + url + " failed: offline cache missed!");
                    errorListener.onErrorResponse(error, tag);
                } else {
                    try {
                        Log.w(LOGTAG, "Getting LC page " + url + " failed: offline cache hit!");
                        LinkedConnections result = getLinkedConnectionsFromJson(new JSONObject(cache.data));
                        successListener.onSuccessResponse(result, tag);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        errorListener.onErrorResponse(error, tag);
                    }
                }
            }
        };

        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                                                               volleySuccessListener,
                                                               volleyErrorListener);
        jsObjRequest.setShouldCache(true);
        jsObjRequest.setRetryPolicy(requestPolicy);
        //Log.i(LOGTAG, "Cached? " + url + ": " + (requestQueue.getCache().get(url) == null ? "empty" : (requestQueue.getCache().get(url).isExpired() ? "expired" : "valid")));
        requestQueue.add(jsObjRequest);
    }

    @NonNull
    private LinkedConnections getLinkedConnectionsFromJson(JSONObject response) throws JSONException {
        LinkedConnections result = new LinkedConnections();
        result.current = response.getString("@id");
        result.next = response.getString("hydra:next");
        result.previous = response.getString("hydra:previous");

        JSONArray array = response.getJSONArray("@graph");
        List<LinkedConnection> connections = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(
                    i);

            if (!entry.has("gtfs:dropOffType") || !entry.has("gtfs:pickupType")) {
                continue;
            }

            LinkedConnection connection = new LinkedConnection();

            connection.uri = entry.getString("@id");

            connection.departureStationUri = entry.getString("departureStop");
            connection.departureTime = DateTime.parse(entry.getString("departureTime"));
            connection.departureDelay = 0;
            if (entry.has("departureDelay")) {
                connection.departureDelay = entry.getInt("departureDelay");
            }

            connection.arrivalStationUri = entry.getString("arrivalStop");
            connection.arrivalTime = DateTime.parse(entry.getString("arrivalTime"));

            connection.arrivalDelay = 0;
            if (entry.has("arrivalDelay")) {
                connection.arrivalDelay = entry.getInt("arrivalDelay");
            }

            connection.direction = entry.getString("direction");
            connection.route = entry.getString("gtfs:route");
            connection.trip = entry.getString("gtfs:trip");

            connections.add(connection);
        }

        result.connections = new LinkedConnection[connections.size()];
        result.connections = connections.toArray(result.connections);
        return result;
    }

    class LinkedConnections {
        String current, previous, next;
        LinkedConnection[] connections = new LinkedConnection[0];
    }

    class LinkedConnection {
        String uri;
        String departureStationUri, arrivalStationUri;
        DateTime departureTime, arrivalTime;
        int departureDelay, arrivalDelay;
        String direction;
        String route, trip;
    }

    class LiveboardResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {
        final ArrayList<LinkedConnection> arrivals = new ArrayList<>();
        final ArrayList<LinkedConnection> departures = new ArrayList<>();
        final ArrayList<VehicleStop> stops = new ArrayList<>();

        // Both departures and arrivals are in chronological order. We'll search to see if we can find a departure which matches an arrival, but only start looking AFTER this arrival.
        final ArrayList<Integer> departureIndexForArrivals = new ArrayList<>();
        private IrailLiveboardRequest request;

        LiveboardResponseListener(IrailLiveboardRequest request) {
            this.request = request;
        }

        @Override
        public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {

            for (LinkedConnection connection : data.connections
                    ) {
                if (Objects.equals(connection.departureStationUri,
                                   request.getStation().getSemanticId())) {
                    departures.add(connection);
                }
                if (Objects.equals(connection.arrivalStationUri,
                                   request.getStation().getSemanticId())) {
                    arrivals.add(connection);
                    departureIndexForArrivals.add(departures.size());
                }
            }

            if (request.getTimeDefinition() == RouteTimeDefinition.DEPART && departures.size() > 20 || request.getTimeDefinition() == RouteTimeDefinition.ARRIVE && arrivals.size() > 20) {
                VehicleStop[] stoparray = generateStopArray();
                request.notifySuccessListeners(new LiveBoard(request.getStation(), stoparray, request.getSearchTime(), request.getTimeDefinition()));

            } else {
                getLinkedConnectionByUrl(data.next,
                                         this,
                                         new IRailErrorResponseListener() {
                                             @Override
                                             public void onErrorResponse(@NonNull Exception e, Object tag) {
                                                 Log.w(LOGTAG, "Getting next LC page failed");
                                             }
                                         },
                                         tag);
            }

        }

        @Override
        public void onErrorResponse(@NonNull Exception e, Object tag) {
            request.notifyErrorListeners(e);
        }

        private VehicleStop[] generateStopArray() {
            // Find stops (train arrives and leaves again)
            ArrayList<LinkedConnection> handledConnections = new ArrayList<>();

            for (int i = 0; i < arrivals.size(); i++) {
                boolean foundMatchingDeparture = false;

                for (int j = departureIndexForArrivals.get(i); j < departures.size() && !foundMatchingDeparture; j++) {

                    if (Objects.equals(arrivals.get(i).trip, departures.get(j).trip)) {
                        foundMatchingDeparture = true;

                        LinkedConnection departure = departures.get(j);
                        LinkedConnection arrival = arrivals.get(i);

                        handledConnections.add(departure);
                        handledConnections.add(arrival);

                        Station direction = IrailFactory.getStationsProviderInstance().getStationByName(
                                departure.direction);

                        stops.add(new VehicleStop(request.getStation(), direction,
                                                  new VehicleStub(
                                                          departure.route.substring(departure.route.lastIndexOf('/') + 1),
                                                          direction,
                                                          departure.route),
                                                  "?",
                                                  true,
                                                  departure.departureTime,
                                                  arrival.arrivalTime,
                                                  Duration.standardSeconds(departure.departureDelay),
                                                  Duration.standardSeconds(arrival.arrivalDelay),
                                                  false,
                                                  false,
                                                  false,
                                                  departure.uri,
                                                  OccupancyLevel.UNKNOWN,
                                                  VehicleStopType.STOP));
                    }
                }
            }

            if (request.getTimeDefinition() == RouteTimeDefinition.DEPART) {
                for (int i = 0; i < departures.size(); i++) {
                    if (!handledConnections.contains(departures.get(i))) {
                        LinkedConnection departure = departures.get(i);
                        Station direction = IrailFactory.getStationsProviderInstance().getStationByName(
                                departure.direction);

                        stops.add(new VehicleStop(request.getStation(), direction, new VehicleStub(
                                departure.route.substring(departure.route.lastIndexOf('/') + 1),
                                direction,
                                departure.route),
                                                  "?",
                                                  true,
                                                  departure.departureTime,
                                                  null,
                                                  Duration.standardSeconds(departure.departureDelay),
                                                  null,
                                                  false,
                                                  false,
                                                  false,
                                                  departure.uri,
                                                  OccupancyLevel.UNKNOWN,
                                                  VehicleStopType.DEPARTURE));
                    }
                }

                Collections.sort(stops, new Comparator<VehicleStop>() {
                    @Override
                    public int compare(VehicleStop o1, VehicleStop o2) {
                        return o1.getDepartureTime().compareTo(o2.getDepartureTime());
                    }
                });
            } else {
                for (int i = 0; i < arrivals.size(); i++) {
                    if (!handledConnections.contains(arrivals.get(i))) {
                        LinkedConnection arrival = arrivals.get(i);
                        Station direction = request.getStation();

                        stops.add(new VehicleStop(request.getStation(), direction, new VehicleStub(
                                arrival.route.substring(arrival.route.lastIndexOf('/') + 1),
                                direction,
                                arrival.route),
                                                  "?",
                                                  true,
                                                  null,
                                                  arrival.arrivalTime,
                                                  null,
                                                  Duration.standardSeconds(arrival.arrivalDelay),
                                                  false,
                                                  false,
                                                  false,
                                                  arrival.uri,
                                                  OccupancyLevel.UNKNOWN,
                                                  VehicleStopType.ARRIVAL));
                    }
                }

                Collections.sort(stops, new Comparator<VehicleStop>() {
                    @Override
                    public int compare(VehicleStop o1, VehicleStop o2) {
                        return o1.getArrivalTime().compareTo(o2.getArrivalTime());
                    }
                });
            }


            VehicleStop[] stoparray = new VehicleStop[stops.size()];
            stops.toArray(stoparray);
            return stoparray;
        }
    }

    class VehicleResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {

        private IrailVehicleRequest mRequest;

        VehicleResponseListener(IrailVehicleRequest request) {
            mRequest = request;
        }

        @Override
        public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {

            List<VehicleStop> stops = new ArrayList<>();
            Log.i(LOGTAG, "Parsing train...");
            LinkedConnection lastConnection = null;
            for (int i = 0; i < data.connections.length; i++) {
                LinkedConnection connection = data.connections[i];
                if (!Objects.equals(connection.route, "http://irail.be/vehicle/" + Vehicle.getTrainType(mRequest.getTrainId()) + Vehicle.getVehicleNumber(mRequest.getTrainId()))) {
                    continue;
                }

                Station departure = IrailFactory.getStationsProviderInstance().getStationById("BE.NMBS." + connection.departureStationUri.substring(connection.departureStationUri.lastIndexOf('/') + 1));
                Station direction = IrailFactory.getStationsProviderInstance().getStationByName(connection.direction);

                if (stops.size() == 0) {
                    // First stop
                    stops.add(VehicleStop.buildDepartureTrainstop(departure, direction, new VehicleStub(connection.route.substring(connection.route.lastIndexOf('/') + 1), direction, connection.route), "?", true,
                                                                  connection.departureTime,
                                                                  Duration.standardSeconds(connection.departureDelay),
                                                                  false, false,
                                                                  connection.uri, OccupancyLevel.UNKNOWN));
                } else {
                    // Some stop during the journey
                    assert lastConnection != null;
                    stops.add(new VehicleStop(departure, direction, new VehicleStub(connection.route.substring(connection.route.lastIndexOf('/') + 1), direction, connection.route), "?", true,
                                              connection.departureTime, lastConnection.arrivalTime,
                                              Duration.standardSeconds(connection.departureDelay),
                                              Duration.standardSeconds(lastConnection.arrivalDelay),
                                              false, false, false,
                                              connection.uri, OccupancyLevel.UNKNOWN, VehicleStopType.STOP));
                }

                lastConnection = connection;
            }

            if (stops.size() > 0 && lastConnection != null) {
                Station arrival = IrailFactory.getStationsProviderInstance().getStationById("BE.NMBS." + lastConnection.arrivalStationUri.substring(lastConnection.arrivalStationUri.lastIndexOf('/') + 1));
                Station direction = IrailFactory.getStationsProviderInstance().getStationByName(lastConnection.direction);

                // Arrival stop
                stops.add(VehicleStop.buildArrivalTrainstop(arrival, direction, new VehicleStub(lastConnection.route.substring(lastConnection.route.lastIndexOf('/') + 1), direction, lastConnection.route),
                                                            "?", true,
                                                            lastConnection.arrivalTime,
                                                            Duration.standardSeconds(lastConnection.arrivalDelay),
                                                            false, false,
                                                            lastConnection.uri, OccupancyLevel.UNKNOWN));

                VehicleStop[] stopsArray = new VehicleStop[stops.size()];
                mRequest.notifySuccessListeners(new Vehicle(stops.get(0).getTrain().getId(), lastConnection.route, stops.get(stops.size() - 1).getStation(), stops.get(0).getStation(), 0, 0, stops.toArray(stopsArray)));
            }
        }

        @Override
        public void onErrorResponse(@NonNull Exception e, Object tag) {
            Log.w(LOGTAG, "Failed to load page! " + e.getMessage());
        }
    }
}
