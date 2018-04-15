package be.hyperrail.android.irail.implementation.linkedconnections;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import be.hyperrail.android.BuildConfig;
import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */

public class LinkedConnectionsProvider {

    private final LinkedConnectionsOfflineCache mLinkedConnectionsOfflineCache;
    private final RequestQueue requestQueue;
    private final RetryPolicy requestPolicy;
    private final ConnectivityManager mConnectivityManager;
    
    private boolean mCacheEnabled = true;

    private static final String UA = "HyperRail for Android - " + BuildConfig.VERSION_NAME;

    private boolean isInternetAvailable() {
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    public LinkedConnectionsProvider(Context context) {
        this.mLinkedConnectionsOfflineCache = new LinkedConnectionsOfflineCache(context);
        this.requestQueue = Volley.newRequestQueue(context);
        this.requestPolicy = new DefaultRetryPolicy(
                1000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void getLinkedConnectionsByDate(DateTime startTime, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {
        startTime = startTime.withMillisOfSecond(0);
        startTime = startTime.withSecondOfMinute(0);
        startTime = startTime.minusMinutes(startTime.getMinuteOfHour() % 10);

        String url = "https://graph.irail.be/sncb/connections?departureTime=" +
                startTime.withZone(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());

        getLinkedConnectionByUrl(url, successListener, errorListener, tag);
    }

    public void getLinkedConnectionsByDateForTimeSpan(DateTime startTime, DateTime endTime, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {

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
                    Log.d("LCProvider", "Compiling larger page, received " + data.current + " STORE " + id);
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
                    Log.d("LCProvider", "Compiling larger page, failed to receive STORE " + id);
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


    void getLinkedConnectionByUrl(final String url, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, final Object tag) {
        // https://graph.irail.be/sncb/connections?departureTime={ISO8601}
        // Log.i(LOGTAG, "Loading " + url);
        // TODO: prevent loading the same URL twice when two requests are made short after each other (locking based on URL)

        Response.Listener<JSONObject> volleySuccessListener = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.w("LCProvider", "Getting LC page successful: " + url);
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
                Log.w("LCProvider", "Getting LC page " + url + " failed: " + error.getMessage());
                LinkedConnectionsOfflineCache.CachedLinkedConnections cache = mLinkedConnectionsOfflineCache.load(url);
                if (cache == null) {
                    Log.w("LCProvider", "Getting LC page " + url + " failed: offline cache missed!");
                    errorListener.onErrorResponse(error, tag);
                } else {
                    try {
                        Log.w("LCProvider", "Getting LC page " + url + " failed: offline cache hit!");
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
                                                               volleyErrorListener){
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-agent", UA);
                return headers;
            }
        };

        jsObjRequest.setShouldCache(mCacheEnabled);
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

    public void setCacheEnabled(boolean cacheEnabled) {
        mCacheEnabled = cacheEnabled;
    }
}
