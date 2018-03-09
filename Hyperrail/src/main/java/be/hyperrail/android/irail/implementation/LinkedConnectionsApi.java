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
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import be.hyperrail.android.BuildConfig;
import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
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

    private final IrailStationProvider mStationsProvider;
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
        this.mStationsProvider = IrailFactory.getStationsProviderInstance();
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
    public void getRoutes(@NonNull IrailRoutesRequest... requests) {
        for (IrailRoutesRequest request :
                requests) {
            getRoutes(request);
        }
    }

    private void getRoutes(@NonNull IrailRoutesRequest request) {

        DateTime departureLimit;

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART) {
            departureLimit = request.getSearchTime();
        } else {
            departureLimit = request.getSearchTime().minusHours(24);
        }

        RouteResponseListener listener = new RouteResponseListener(request, departureLimit);

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART) {
            getLinkedConnectionsByDateForTimeSpan(request.getSearchTime(), request.getSearchTime().plusHours(6), listener, listener, null);
        } else {
            getLinkedConnectionsByDateForTimeSpan(request.getSearchTime().minusHours(1), request.getSearchTime(), listener, listener, null);
        }
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
                startTime.withZone(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());

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
                Log.w(LOGTAG, "Getting LC page successful: " + url);
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

            if (!entry.has("gtfs:dropOffType") ||
                    !entry.has("gtfs:pickupType") ||
                    !Objects.equals(entry.getString("gtfs:dropOffType"), "gtfs:Regular") ||
                    !Objects.equals(entry.getString("gtfs:pickupType"), "gtfs:Regular")) {
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
        Arrays.sort(result.connections, new Comparator<LinkedConnection>() {
            @Override
            public int compare(LinkedConnection o1, LinkedConnection o2) {
                return o1.departureTime.compareTo(o2.departureTime);
            }
        });
        return result;
    }

    private static String basename(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static String uriToId(String uri) {
        return "BE.NMBS." + basename(uri);
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
                                                          basename(departure.route),
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
                        Station direction = mStationsProvider.getStationByName(
                                departure.direction);

                        stops.add(new VehicleStop(request.getStation(), direction, new VehicleStub(
                                basename(departure.route),
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
                                basename(arrival.route),
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

                Station departure = mStationsProvider.getStationById(uriToId(connection.departureStationUri));
                Station direction = mStationsProvider.getStationByName(connection.direction);

                if (stops.size() == 0) {
                    // First stop
                    stops.add(VehicleStop.buildDepartureTrainstop(departure, direction, new VehicleStub(basename(connection.route), direction, connection.route), "?", true,
                                                                  connection.departureTime,
                                                                  Duration.standardSeconds(connection.departureDelay),
                                                                  false, false,
                                                                  connection.uri, OccupancyLevel.UNKNOWN));
                } else {
                    // Some stop during the journey
                    assert lastConnection != null;
                    stops.add(new VehicleStop(departure, direction, new VehicleStub(basename(connection.route), direction, connection.route), "?", true,
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
                stops.add(VehicleStop.buildArrivalTrainstop(arrival, direction, new VehicleStub(basename(lastConnection.route), direction, lastConnection.route),
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

    class RouteResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {

        IrailRoutesRequest mRoutesRequest;
        private final DateTime mDepartureLimit;
        int maxTransfers = 3;

        // This makes a lot of checks easiers
        DateTime infinite = new DateTime(3000, 1, 1, 0, 0);

        // For each stop, keep an array of (departuretime, arrivaltime) pairs
        // After execution, this array will contain the xt profile for index x
        // Size n, where n is the number of stations
        // Each entry in this array is an array of  (departuretime, arrivaltime) pairs, sorted by DESCENDING departuretime
        // A DESCENDING departurtime will ensure we always add to the back of the array, thus saving O(n) operations every time!
        // Note: for journey extraction, 2 data fields will be added. These fields can be ignored for the original Profile Connection Scan Algorithm
        HashMap<String, List<StationQuadruple>> S = new HashMap<>();

        // For every trip, keep the earliest possible arrival time
        // The earliest arrival time for the partial journey departing in the earliest scanned connection of the corresponding trip
        // Size m, where m is the number of trips
        HashMap<String, TrainTriple> T = new HashMap<>();

        RouteResponseListener(IrailRoutesRequest request, DateTime departureLimit) {
            mRoutesRequest = request;
            mDepartureLimit = departureLimit;
        }

        private void process(LinkedConnections data) {
            // Keep searching
            // - while no results have been found
            // - until we have the number of results we'd like (in case no departure time is given)
            // - but stop when we're passing the departe time limit
            // - when we're searching with a departuretime, we need to continue until we're at the front. This might result in more results, which we'll all pass to the client

            if (data.connections.length == 0) {
                getLinkedConnectionByUrl(data.previous, this, this, null);
            }

            boolean hasPassedDepartureLimit = false;
            for (int i = data.connections.length - 1; i >= 0; i--) {
                LinkedConnection connection = data.connections[i];

                // TODO: filter too late / too early
                if (connection.departureTime.isBefore(mDepartureLimit)) {
                    hasPassedDepartureLimit = true;
                    continue;
                }

                // ====================================================== //
                // START GET EARLIEST ARRIVAL TIME
                // ====================================================== //

                DateTime T1_walkingArrivalTime, T2_stayOnTripArrivalTime, T3_transferArrivalTime;
                int T1_transfers, T2_transfers, T3_transfers;

                // Log::info((new Station($connection->getDepartureStopUri()))->getDefaultName() .' - '.(new Station($connection->getArrivalStopUri()))->getDefaultName() .' - '. $connection->getRoute());
                // Determine T1, the time when walking from here to the destination
                if (Objects.equals(connection.arrivalStationUri, mRoutesRequest.getDestination().getSemanticId())) {
                    // If this connection ends at the destination, we can walk from here to tthe station exit.
                    // Our implementation does not add a footpath at the end
                    // Therefore, we arrive at our destination at the time this connection arrives
                    T1_walkingArrivalTime = connection.arrivalTime;
                    // We're walking, so this connections has no transfers between it and the destination
                    T1_transfers = 0;
                    // Log::info("[{$connection->getId()}] Walking possible with arrival time  $T1_walkingArrivalTime.");
                } else {
                    // When this isn't the destination stop, we would arrive somewhere far, far in the future.
                    // We're walking infinitly slow: we prefer a train
                    // For stops which are close to each other, we could walk to another stop to take a train there
                    // This is to be supported later on, but requires a list of footpaths.
                    // TODO: support walking to a nearby stop, e.g. haren/haren-zuid
                    T1_walkingArrivalTime = infinite;
                    // Default value to prevent errors due to undefined variables.
                    // Will never be used: when an infinitely late arrival is to earliest available, the for loop will skip to the next connection.
                    T1_transfers = 999;
                    // Log::info("[{$connection->getId()}] Walking not possible.");
                }
                // Determine T2, the first possible time of arrival when remaining seated
                if (T.containsKey(connection.trip)) {
                    // When we remain seated on this train, we will arrive at the fastest arrival time possible for this vehicle
                    T2_stayOnTripArrivalTime = T.get(connection.trip).arrivalTime;
                    // Remaining seated will have the same number of transfers between this connection and the destination, as from the best exit stop and the destination
                    T2_transfers = T.get(connection.trip).transfers;
                    // Log::info("[{$connection->getId()}] Remaining seated possible with arrival time $T2_stayOnTripArrivalTime and $T2_transfers transfers.");
                } else {
                    // When there isn't a fastest arrival time for this stop yet, it means we haven't found a connection
                    // - To arrive in the destination using this vehicle, or
                    // - To transfer to another vehicle in another station
                    T2_stayOnTripArrivalTime = infinite;
                    // Default value to prevent errors due to undefined variables.
                    // Will never be used: when an infinitely late arrival is to earliest available, the for loop will skip to the next connection.
                    T2_transfers = 999;
                    // Log::info("[{$connection->getId()}] Remaining seated not possible");
                }
                // Determine T3, the time of arrival when taking the best possible transfer in this station
                if (S.containsKey(connection.arrivalStationUri)) {
                    // If there are connections leaving from the arrival station, determine the one which departs after we arrive,
                    // but arrives as soon as possible
                    // The earliest departure is in the back of the array. This int will keep track of which pair we're evaluating.
                    int position = S.get(connection.arrivalStationUri).size() - 1;
                    StationQuadruple quadruple = S.get(connection.arrivalStationUri).get(position);

                    // TODO: replace hard-coded transfer time
                    // As long as we're arriving AFTER the pair departure, move forward in the list until we find a departure which is reachable
                    // The list is sorted by descending departure time, so the earliest departures are in the back (so we move back to front)

                    while ((quadruple.departureTime.getMillis() - 300 * 1000 <= connection.arrivalTime.getMillis() ||
                            quadruple.transfers >= maxTransfers) && position > 0) {
                        position--;
                        quadruple = S.get(connection.arrivalStationUri).get(position);
                    }
                    if (quadruple.departureTime.getMillis() - 300 * 1000 > connection.arrivalTime.getMillis() && quadruple.transfers <= maxTransfers) {
                        // If a result was found in the list, this is the earliest arrival time when transferring here
                        // Optional: Adding one second to the arrival time will ensure that the route with the smallest number of legs is chosen.
                        // This would not affect journey extaction, but would prefer routes with less legs when arrival times are identical (as their arrival time will be one second earlier)
                        // It would prefer remaining seated over transferring when both would result in the same arrival time
                        // TODO: increase to 240 -> this way we prefer one less transfer in exchange for 10 minutes longer trip
                        // See http://lc2irail.dev/connections/008822160/008895257/departing/1519924311
                        T3_transferArrivalTime = new DateTime(quadruple.arrivalTime.getMillis() + 240 * 1000);
                        // Using this transfer will increase the number of transfers with 1
                        T3_transfers = quadruple.transfers + 1;
                        // Duration transferTime = new Duration(connection.arrivalTime, quadruple.departureTime);
                        // Log::info("[{$connection->getId()}] Transferring possible with arrival time $T3_transferArrivalTime and $T3_transfers transfers. Transfer time is $transferTime.");
                    } else {
                        // When there isn't a reachable connection, transferring isn't an option
                        T3_transferArrivalTime = infinite;
                        // Default value to prevent errors due to undefined variables.
                        // Will never be used: when an infinitely late arrival is to earliest available, the for loop will skip to the next connection.
                        T3_transfers = 999;
                        // Log::info("[{$connection->getId()}] Transferring not possible: No transfers reachable");
                    }
                } else {
                    // When there isn't a reachable connection, transferring isn't an option
                    T3_transferArrivalTime = infinite;
                    // Default value to prevent errors due to undefined variables.
                    // Will never be used: when an infinitely late arrival is to earliest available, the for loop will skip to the next connection.
                    T3_transfers = 999;
                    // Log::info("[{$connection->getId()}] Transferring not possible: No transfers exist");
                }

                // Tmin = Tc in the paper
                // This is the earliest arrival time over the 3 possibilities
                DateTime Tmin;
                LinkedConnection exitTrainConnection;
                int numberOfTransfers;
                // Where do we need to get off the train?
                // The following if-else structure does not follow the JourneyLeg Extraction algorithm as described in the CSA (march 2017) paper.
                // Not only do we want to reconstruct the journey (the vehicles used), but we want departure and arrival times for every single leg.
                // In order to also have the arrival times, we will always save the arrival connection for the next hop, instead of the arrival connection for the entire journey.
                // If T3 < T2, prefer a transfer.
                // If T3 == T2, prefer T3. This ensures we don't go A - (B) - (C) - D - (C) - (B) - E when searching A-E but A - B - E instead
                // Here we force the least amount of transfers for the same arrival time since T3 is already incremented with some seconds
                if (T3_transferArrivalTime.getMillis() <= T2_stayOnTripArrivalTime.getMillis()) {
                    // Log::info("Transfer time!");
                    Tmin = T3_transferArrivalTime;
                    // We're transferring here, so get off the train in this station
                    exitTrainConnection = connection;
                    // We already incremented this transfer counter when determining the train
                    numberOfTransfers = T3_transfers;
                } else {
                    // Log::info("Train time!");
                    Tmin = T2_stayOnTripArrivalTime;
                    // We're staying on this trip. This also implicates a key in T exists for this trip. We're getting off at the previous exit for this vehicle.
                    if (T2_stayOnTripArrivalTime.isBefore(infinite)) {
                        exitTrainConnection = T.get(connection.trip).arrivalConnection;
                    } else {
                        exitTrainConnection = null;
                    }
                    numberOfTransfers = T2_transfers;
                }
                // For equal times, we prefer just arriving.
                if (T1_walkingArrivalTime.getMillis() <= Tmin.getMillis()) {
                    // Log::info("Nvm, walking time!");
                    Tmin = T1_walkingArrivalTime;
                    // We're walking from here, so get off here
                    exitTrainConnection = connection;
                    numberOfTransfers = T1_transfers;
                }
                // ====================================================== //
                // END GET EARLIEST ARRIVAL TIME
                // ====================================================== //

                // The exitTrainConnection condition is unnecessary, but will prevent warnings about possible nullReferenceErrors
                if (Tmin.isEqual(infinite) || exitTrainConnection == null) {
                    continue;
                }

                // We now have the minimal arrival time for this connection
                // Update T and S with this new data
                // ====================================================== //
                // START UPDATE T
                // ====================================================== //
                // Set the fastest arrival time for this vehicle, and set the connection at which we have to hop off
                if (T.containsKey(connection.trip)) {

                    // When there is a faster way for this trip, it's by getting of at this connection's arrival station and transferring (or having arrived)

                    // Can also be equal for a transfer with the best transfer (don't do bru south - central - north - transfer - north - central - south
                    // We're updating an existing connection, with a way to get off earlier (iterating using descending departure times).
                    // This only modifies the transfer stop, nothing else in the journey
                    if (Tmin.isEqual(T.get(connection.trip).arrivalTime)
                            && T3_transferArrivalTime.isEqual(T2_stayOnTripArrivalTime)
                            && S.containsKey(T.get(connection.trip).arrivalConnection.arrivalStationUri)
                            && S.containsKey(connection.arrivalStationUri)
                            ) {
                        // When the arrival time is the same, the number of transfers should also be the same
                        // We prefer the exit connection with the largest transfer time
                        // Suppose we exit the train here: connection. Does this improve on the transfer time?
                        LinkedConnection currentTrainExit = T.get(connection.trip).arrivalConnection;
                        // Now we need the departure in the next station!
                        // Create a quadruple to lookup the first reachable connection in S
                        // Create one, because we don't know where we'd get on this train

                        StationQuadruple quad = new StationQuadruple();
                        quad.departureTime = connection.departureTime;
                        quad.departureConnection = connection;
                        // Current situation
                        quad.arrivalTime = Tmin;
                        quad.arrivalConnection = currentTrainExit;

                        Duration currentTransfer = new Duration(currentTrainExit.arrivalTime, getFirstReachableConnection(quad).departureTime);

                        // New situation
                        quad.arrivalTime = Tmin;
                        quad.arrivalConnection = exitTrainConnection;
                        Duration newTransfer = new Duration(exitTrainConnection.arrivalTime, getFirstReachableConnection(quad).departureTime);

                        // If the new situation is better
                        if (newTransfer.isLongerThan(currentTransfer)) {
                            TrainTriple triple = new TrainTriple();
                            triple.arrivalTime = Tmin;
                            triple.arrivalConnection = exitTrainConnection;
                            triple.transfers = numberOfTransfers;

                            T.put(connection.trip, triple);
                        }
                    }

                    // Faster way
                    if (Tmin.isBefore(T.get(connection.trip).arrivalTime)) {
                        // exit = (new Station(exitTrainConnection->getArrivalStopUri()))->getDefaultName();
                        // Log::info("[{connection->getId()}] Updating T: Arrive at Tmin using {connection->getRoute()} with numberOfTransfers transfers. Get off at {exit}.");
                        TrainTriple triple = new TrainTriple();
                        triple.arrivalTime = Tmin;
                        triple.arrivalConnection = exitTrainConnection;
                        triple.transfers = numberOfTransfers;

                        T.put(connection.trip, triple);
                    }
                } else {
                    // exit = (new Station(exitTrainConnection->getArrivalStopUri()))->getDefaultName();
                    // Log::info("[{connection->getId()}] Updating T: New: Arrive at Tmin using {connection->getRoute()} with numberOfTransfers transfers. Get off at {exit}.");
                    // To travel towards the destination, get off at the current arrival station (followed by a transfer or walk/arriving)
                    TrainTriple triple = new TrainTriple();
                    triple.arrivalTime = Tmin;
                    triple.arrivalConnection = exitTrainConnection;
                    triple.transfers = numberOfTransfers;
                    T.put(connection.trip, triple);
                }
                // ====================================================== //
                // END UPDATE T
                // ====================================================== //

                // ====================================================== //
                // START UPDATE S
                // ====================================================== //

                // Create a quadruple to update S
                StationQuadruple quad = new StationQuadruple();
                quad.departureTime = connection.departureTime;
                quad.arrivalTime = Tmin;
                // Additional data for journey extraction
                quad.departureConnection = connection;
                quad.arrivalConnection = T.get(connection.trip).arrivalConnection;
                quad.transfers = numberOfTransfers;
                if (S.containsKey(connection.departureStationUri)) {
                    int numberOfPairs = S.get(connection.departureStationUri).size();
                    StationQuadruple existingQuad = S.get(connection.departureStationUri).get(numberOfPairs - 1);
                    // If existingQuad does not dominate quad
                    // The new departure time is always less or equal than an already stored one
                    if (quad.arrivalTime.isBefore(existingQuad.arrivalTime)) {
                        // // Log::info("[{connection->getId()}] Updating S: Reach destination from departureStop departing at {quad[self::KEY_DEPARTURE_TIME]} arriving at {quad[self::KEY_ARRIVAL_TIME]}");
                        if (quad.departureTime.isEqual(existingQuad.departureTime)) {
                            // Replace existingQuad at the back
                            S.get(connection.departureStationUri).remove(numberOfPairs - 1);
                            S.get(connection.departureStationUri).add(numberOfPairs - 1, quad);
                        } else {
                            // We're iterating over descending departure times, therefore the departure
                            // Insert at the back
                            S.get(connection.departureStationUri).add(quad);
                        }
                    }
                } else {
                    // Log::info("[{connection->getId()}] Updating S: New: Reach destination from departureStop departing at {quad[self::KEY_DEPARTURE_TIME]} arriving at {quad[self::KEY_ARRIVAL_TIME]}");
                    S.put(connection.departureStationUri, new ArrayList<StationQuadruple>());
                    S.get(connection.departureStationUri).add(quad);
                }
                // ====================================================== //
                // END UPDATE S
                // ====================================================== //
            }

            // No results? load more data or stop if we passed the departure time limit
            if (!S.containsKey(mRoutesRequest.getOrigin().getSemanticId())) {
                if (hasPassedDepartureLimit) {
                    RouteResult result = new RouteResult(mRoutesRequest.getOrigin(), mRoutesRequest.getDestination(), mRoutesRequest.getSearchTime(), mRoutesRequest.getTimeDefinition(), new Route[0]);
                    mRoutesRequest.notifySuccessListeners(result);
                } else {
                    getLinkedConnectionByUrl(data.previous, this, this, null);
                }
                return;
            }

            // Results? Return data
            Route[] routes = new Route[S.get(mRoutesRequest.getOrigin().getSemanticId()).size()];

            int i = 0;
            for (StationQuadruple quad : S.get(mRoutesRequest.getOrigin().getSemanticId())
                    ) {
                // it will iterate over all legs
                StationQuadruple it = quad;
                List<RouteLeg> legs = new ArrayList<>();
                List<Transfer> transfers = new ArrayList<>();

                RouteLeg r = new RouteLeg(RouteLegType.TRAIN, new VehicleStub(basename(quad.departureConnection.route), mStationsProvider.getStationByName(quad.departureConnection.direction), quad.departureConnection.trip));
                Transfer t = new Transfer(mRoutesRequest.getOrigin(),
                                          null, null, "?", true, Duration.ZERO, false, true,
                                          r, it.departureConnection.departureTime, "?", true, Duration.standardSeconds(quad.departureConnection.departureDelay), false, true,
                                          it.departureConnection.uri, OccupancyLevel.UNKNOWN, TransferType.DEPARTURE);

                legs.add(r);
                transfers.add(t);

                Transfer lastTransfer = t;
                RouteLeg lastLeg = r;

                while (!Objects.equals(it.arrivalConnection.arrivalStationUri, mRoutesRequest.getDestination().getSemanticId())) {
                    StationQuadruple next = getFirstReachableConnection(it);

                    r = new RouteLeg(RouteLegType.TRAIN, new VehicleStub(basename(next.departureConnection.route), mStationsProvider.getStationByName(next.departureConnection.direction), next.departureConnection.trip));
                    if (!Objects.equals(it.arrivalConnection.arrivalStationUri, mRoutesRequest.getDestination().getSemanticId())) {
                        t = new Transfer(mStationsProvider.getStationById(uriToId(it.arrivalConnection.arrivalStationUri)),
                                         lastLeg, it.arrivalTime, "?", true, Duration.standardSeconds(it.arrivalConnection.arrivalDelay), false, true,
                                         r, next.departureConnection.departureTime, "?", true, Duration.standardSeconds(quad.departureConnection.departureDelay), false, true,
                                         next.departureConnection.uri, OccupancyLevel.UNKNOWN, TransferType.TRANSFER);
                    } else {
                        t = new Transfer(mStationsProvider.getStationById(uriToId(it.arrivalConnection.arrivalStationUri)),
                                         lastLeg, it.arrivalTime, "?", true, Duration.standardSeconds(it.arrivalConnection.arrivalDelay), false, true,
                                         null, null, "?", true, Duration.ZERO, false, true,
                                         next.departureConnection.uri, OccupancyLevel.UNKNOWN, TransferType.ARRIVAL);
                    }

                    legs.add(r);
                    transfers.add(t);
                    lastLeg = r;
                    lastTransfer = t;

                    it = next;
                }

                Transfer[] transferArray = new Transfer[transfers.size()];
                RouteLeg[] legsArray = new RouteLeg[legs.size()];
                routes[i++] = new Route(mRoutesRequest.getOrigin(), mRoutesRequest.getDestination(),
                                        transfers.get(0).getDepartureTime().withZone(DateTimeZone.getDefault()), transfers.get(0).getDepartureDelay(), "?", true,
                                        lastTransfer.getArrivalTime().withZone(DateTimeZone.getDefault()), lastTransfer.getArrivalDelay(), "?", true,
                                        legs.toArray(legsArray), transfers.toArray(transferArray),
                                        new Message[0], new Message[legs.size()][], new Message[0]);

            }

            Arrays.sort(routes, new Comparator<Route>() {
                @Override
                public int compare(Route o1, Route o2) {
                    return o1.getDepartureTime().compareTo(o2.getDepartureTime());
                }
            });

            RouteResult result = new RouteResult(mRoutesRequest.getOrigin(), mRoutesRequest.getDestination(), mRoutesRequest.getSearchTime(), mRoutesRequest.getTimeDefinition(), routes);
            mRoutesRequest.notifySuccessListeners(result);
        }

        StationQuadruple getFirstReachableConnection(StationQuadruple arrivalQuad) {
            List<StationQuadruple> it_options = S.get(arrivalQuad.arrivalConnection.arrivalStationUri);
            int i = it_options.size() - 1;
            // Find the next hop. This is the first reachable hop,
            // or even stricter defined: the hop which will get us to the destination at the same arrival time.
            // There will be a one second difference between the arrival times, as a result of the leg optimization
            while (i >= 0 && it_options.get(i).arrivalTime.getMillis() != arrivalQuad.arrivalTime.getMillis() - 240 * 1000) {
                i--;
            }
            return it_options.get(i);
        }

        @Override
        public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {
            process(data);
        }

        @Override
        public void onErrorResponse(@NonNull Exception e, Object tag) {

        }
    }

    class StationQuadruple {
        /**
         * The departure time in this stop
         */
        DateTime departureTime;

        /**
         * The arrival time at the final destination
         */
        DateTime arrivalTime;

        /**
         * The departure connection in this stop
         */
        LinkedConnection departureConnection;

        /**
         * The arrival connection for the next transfer or arrival
         */
        LinkedConnection arrivalConnection;

        /**
         * The number of transfers between standing in this station and the destination
         */
        int transfers;
    }

    class TrainTriple {
        /**
         * The arrival time at the final destination
         */
        DateTime arrivalTime;

        /**
         * The number of transfers until the destination when hopping on to this train
         */
        int transfers;

        /**
         * The arrival connection for the next transfer or arrival
         */
        LinkedConnection arrivalConnection;
    }

}
