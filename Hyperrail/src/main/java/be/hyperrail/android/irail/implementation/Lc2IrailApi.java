package be.hyperrail.android.irail.implementation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.crash.FirebaseCrash;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import be.hyperrail.android.BuildConfig;
import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.irailapi.LiveboardAppendHelper;
import be.hyperrail.android.irail.implementation.irailapi.RouteAppendHelper;
import be.hyperrail.android.irail.implementation.requests.ExtendLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.ExtendRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailDisturbanceRequest;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.IrailPostOccupancyRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRouteRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailVehicleRequest;
import be.hyperrail.android.irail.implementation.requests.VehicleStopRequest;

import static java.util.logging.Level.WARNING;

/**
 * Created in be.hyperrail.android.irail.implementation on 13/04/2018.
 */
public class Lc2IrailApi implements IrailDataProvider {

    private static final String UA = "HyperRail for Android - " + BuildConfig.VERSION_NAME;

    private final Context mContext;
    private final IrailStationProvider mStationsProvider;
    private final Lc2IrailParser parser;
    private final RequestQueue requestQueue;
    private final DefaultRetryPolicy requestPolicy;
    private final ConnectivityManager mConnectivityManager;

    private final static String TAG_IRAIL_API_GET = "LC2IRAIL_GET";

    public Lc2IrailApi(Context context) {
        this.mContext = context;
        this.mStationsProvider = IrailFactory.getStationsProviderInstance();
        this.parser = new Lc2IrailParser(mStationsProvider);
        this.requestQueue = Volley.newRequestQueue(context);
        this.requestPolicy = new DefaultRetryPolicy(
                1500,
                4,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

    }

    @Override
    public void getDisturbances(@NonNull IrailDisturbanceRequest... requests) {
        (new IrailApi(mContext)).getDisturbances(requests);
    }

    @Override
    public void getLiveboard(@NonNull IrailLiveboardRequest... requests) {
        for (IrailLiveboardRequest request : requests) {
            getLiveboard(request);
        }
    }

    private void getLiveboard(@NonNull final IrailLiveboardRequest request) {
        // https://api.irail.be/connections/?to=Halle&from=Brussels-south&date={dmy}&time=2359&timeSel=arrive or depart&format=json
        DateTimeFormatter fmt = ISODateTimeFormat.dateTimeNoMillis();

        // https://lc2irail.thesis.bertmarcelis.be/liveboard/008841004/2018-04-13T13:13:47+00:00
        String url = "https://lc2irail.thesis.bertmarcelis.be/liveboard/"
                + request.getStation().getId().substring(8) + "/"
                + fmt.print(request.getSearchTime());

        Response.Listener<JSONObject> successListener = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Liveboard liveboard;
                try {
                    liveboard = parser.parseLiveboard(request, response);
                } catch (Exception e) {
                    FirebaseCrash.logcat(
                            WARNING.intValue(), "Failed to parse liveboard", e.getMessage());
                    FirebaseCrash.report(e);
                    request.notifyErrorListeners(e);
                    return;
                }
                request.notifySuccessListeners(liveboard);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError e) {
                FirebaseCrash.logcat(
                        WARNING.intValue(), "Failed to get liveboard", e.getMessage());
                request.notifyErrorListeners(e);
            }
        };

        tryOnlineOrServerCache(url, successListener, errorListener);
    }

    @Override
    public void extendLiveboard(@NonNull ExtendLiveboardRequest... requests) {
        for (ExtendLiveboardRequest request :
                requests) {
            LiveboardAppendHelper helper = new LiveboardAppendHelper();
            helper.extendLiveboard(request);
        }
    }

    @Override
    public void getRoutes(@NonNull IrailRoutesRequest... requests) {
        for (IrailRoutesRequest request : requests) {
            getRoutes(request);
        }
    }


    public void getRoutes(@NonNull final IrailRoutesRequest request) {

        // https://api.irail.be/connections/?to=Halle&from=Brussels-south&date={dmy}&time=2359&timeSel=arrive or depart&format=json
        DateTimeFormatter fmt = ISODateTimeFormat.dateTimeNoMillis();

        //https://lc2irail.thesis.bertmarcelis.be/connections/008841004/008814001/departing/2018-04-13T13:13:47+00:00
        String url = "https://lc2irail.thesis.bertmarcelis.be/connections/"
                + request.getOrigin().getId().substring(8) + "/"
                + request.getDestination().getId().substring(8) + "/";
        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            url += "departing/";
        } else {
            url += "arriving/";
        }
        url += fmt.print(request.getSearchTime());

        Response.Listener<JSONObject> successListener = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                RouteResult routeResult = null;
                try {
                    // parse
                } catch (Exception e) {
                    FirebaseCrash.logcat(
                            WARNING.intValue(), "Failed to parse routes", e.getMessage());
                    FirebaseCrash.report(e);
                    request.notifyErrorListeners(e);
                    return;
                }
                request.notifySuccessListeners(routeResult);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError e) {
                FirebaseCrash.logcat(
                        WARNING.intValue(), "Failed to get routes", e.getMessage());
                request.notifyErrorListeners(e);
            }
        };

        tryOnlineOrServerCache(url, successListener, errorListener);
    }

    @Override
    public void extendRoutes(@NonNull ExtendRoutesRequest... requests) {
        for (ExtendRoutesRequest request :
                requests) {
            RouteAppendHelper helper = new RouteAppendHelper();
            helper.extendRoutesRequest(request);
        }
    }

    @Override
    public void getRoute(@NonNull IrailRouteRequest... requests) {
        for (final IrailRouteRequest request : requests) {
            IrailRoutesRequest routesRequest = new IrailRoutesRequest(
                    request.getOrigin(), request.getDestination(), request.getTimeDefinition(),
                    request.getSearchTime()
            );

            // Create a new routerequest. A successful response will be iterated to find a matching route. An unsuccessful query will cause the original error handler to be called.
            routesRequest.setCallback(new IRailSuccessResponseListener<RouteResult>() {
                @Override
                public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
                    for (Route r : data.getRoutes()) {
                        if (r.getTransfers()[0].getDepartureSemanticId() != null &&
                                r.getTransfers()[0].getDepartureSemanticId().equals(request.getDepartureSemanticId())) {
                            request.notifySuccessListeners(r);
                        }
                    }
                }
            }, new IRailErrorResponseListener() {
                @Override
                public void onErrorResponse(@NonNull Exception e, Object tag) {
                    request.notifyErrorListeners(e);
                }
            }, request.getTag());

            getRoutes(routesRequest);
        }
    }

    @Override
    public void getStop(@NonNull VehicleStopRequest... requests) {
        for (VehicleStopRequest request : requests) {
            getStop(request);
        }
    }

    private void getStop(@NonNull final VehicleStopRequest request) {
        DateTime time = request.getStop().getDepartureTime();
        if (time == null) {
            time = request.getStop().getArrivalTime();
        }
        IrailVehicleRequest vehicleRequest = new IrailVehicleRequest(request.getStop().getVehicle().getId(), time);
        vehicleRequest.setCallback(new IRailSuccessResponseListener<Vehicle>() {
            @Override
            public void onSuccessResponse(@NonNull Vehicle data, Object tag) {
                for (VehicleStop stop :
                        data.getStops()) {
                    if (stop.getDepartureSemanticId().equals(request.getStop().getDepartureSemanticId())) {
                        request.notifySuccessListeners(stop);
                        return;
                    }
                }
            }
        }, request.getOnErrorListener(), null);
        getVehicle(vehicleRequest);
    }

    @Override
    public void getVehicle(@NonNull IrailVehicleRequest... requests) {
        for (IrailVehicleRequest request : requests) {
            getVehicle(request);
        }
    }

    public void getVehicle(@NonNull final IrailVehicleRequest request) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTimeNoMillis();

        // https://lc2irail.thesis.bertmarcelis.be/vehicle/IC538/20180413
        String url = "https://lc2irail.thesis.bertmarcelis.be/vehicle/"
                + request.getVehicleId().substring(8) + "/"
                + fmt.print(request.getSearchTime());

        Response.Listener<JSONObject> successListener = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Vehicle vehicle = null;
                try {
                    // parse
                } catch (Exception e) {
                    FirebaseCrash.logcat(
                            WARNING.intValue(), "Failed to parse vehicle", e.getMessage());
                    FirebaseCrash.report(e);
                    request.notifyErrorListeners(e);
                    return;
                }
                request.notifySuccessListeners(vehicle);
            }
        };

        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError e) {
                FirebaseCrash.logcat(
                        WARNING.intValue(), "Failed to get vehicle", e.getMessage());
                request.notifyErrorListeners(e);
            }
        };

        tryOnlineOrServerCache(url, successListener, errorListener);
    }

    @Override
    public void postOccupancy(@NonNull IrailPostOccupancyRequest... requests) {
        new IrailApi(mContext).postOccupancy(requests);
    }

    @Override
    public void abortAllQueries() {

    }

    private boolean isInternetAvailable() {
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }


    /**
     * If internet is available, make a request. Otherwise, check the cache
     *
     * @param url             The url where the request should be made to
     * @param successListener The listener for successful responses, which will be used by the cache
     * @param errorListener   The listener for unsuccessful responses
     */
    private void tryOnlineOrServerCache(String url, Response.Listener<JSONObject> successListener, Response.ErrorListener errorListener) {
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, successListener, errorListener) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-agent", UA);
                return headers;
            }
        };
        jsObjRequest.setRetryPolicy(requestPolicy);
        jsObjRequest.setTag(TAG_IRAIL_API_GET);

        if (isInternetAvailable()) {
            requestQueue.add(jsObjRequest);
        } else {
            if (requestQueue.getCache().get(jsObjRequest.getCacheKey()) != null) {
                try {
                    JSONObject cache;
                    cache = new JSONObject(new String(requestQueue.getCache().get(jsObjRequest.getCacheKey()).data));
                    successListener.onResponse(cache);
                } catch (JSONException e) {
                    FirebaseCrash.logcat(
                            WARNING.intValue(), "Failed to get result from cache", e.getMessage());
                    errorListener.onErrorResponse(new NoConnectionError());
                }

            } else {
                errorListener.onErrorResponse(new NoConnectionError());
            }
        }
    }
}
