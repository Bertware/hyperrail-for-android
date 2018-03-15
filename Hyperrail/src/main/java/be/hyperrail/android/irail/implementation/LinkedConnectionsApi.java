package be.hyperrail.android.irail.implementation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.joda.time.DateTime;

import be.hyperrail.android.BuildConfig;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.irailapi.RouteAppendHelper;
import be.hyperrail.android.irail.implementation.linkedconnections.LinkedConnectionsProvider;
import be.hyperrail.android.irail.implementation.linkedconnections.LiveboardResponseListener;
import be.hyperrail.android.irail.implementation.linkedconnections.RouteResponseListener;
import be.hyperrail.android.irail.implementation.linkedconnections.VehicleResponseListener;
import be.hyperrail.android.irail.implementation.requests.ExtendLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.ExtendRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailDisturbanceRequest;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.IrailPostOccupancyRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRouteRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailVehicleRequest;
import be.hyperrail.android.irail.implementation.requests.VehicleStopRequest;

import static be.hyperrail.android.irail.implementation.Liveboard.LiveboardType.DEPARTURES;

/**
 * This API loads linkedConnection data and builds responses based on this data
 */
public class LinkedConnectionsApi implements IrailDataProvider {

    private final IrailStationProvider mStationsProvider;
    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private Context mContext;
    private static final String LOGTAG = "LinkedConnectionsApi";

    public LinkedConnectionsApi(Context context) {
        this.mContext = context;
        this.mStationsProvider = IrailFactory.getStationsProviderInstance();
        this.mLinkedConnectionsProvider = new LinkedConnectionsProvider(context);
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

    @Override
    public void extendLiveboard(@NonNull ExtendLiveboardRequest... requests) {
        for (final ExtendLiveboardRequest request :
                requests) {
            IrailLiveboardRequest nextRequest;
            DateTime searchTime = request.getLiveboard().getSearchTime();

            if (request.getAction() == ExtendLiveboardRequest.Action.PREPEND) {
                if (request.getLiveboard().getStops().length > 0) {
                    if (request.getLiveboard().getLiveboardType() == DEPARTURES) {
                        searchTime = request.getLiveboard().getStops()[0].getDepartureTime();
                    } else {
                        searchTime = request.getLiveboard().getStops()[0].getArrivalTime();
                    }
                }
                nextRequest = new IrailLiveboardRequest(request.getLiveboard(), RouteTimeDefinition.ARRIVE_AT, request.getLiveboard().getLiveboardType(), searchTime);
            } else {
                int stops = request.getLiveboard().getStops().length;
                if (stops > 0) {
                    if (request.getLiveboard().getLiveboardType() == DEPARTURES) {
                        searchTime = request.getLiveboard().getStops()[stops - 1].getDepartureTime();
                    } else {
                        searchTime = request.getLiveboard().getStops()[stops - 1].getArrivalTime();
                    }
                }
                nextRequest = new IrailLiveboardRequest(request.getLiveboard(), RouteTimeDefinition.DEPART_AT, request.getLiveboard().getLiveboardType(), searchTime);
            }

            nextRequest.setCallback(new IRailSuccessResponseListener<Liveboard>() {
                @Override
                public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
                    Liveboard result = request.getLiveboard();
                    request.notifySuccessListeners(result.withStopsAppended(data));
                }
            }, request.getOnErrorListener(), null);
        }
    }

    private void getLiveboard(@NonNull final IrailLiveboardRequest request) {
        LiveboardResponseListener listener = new LiveboardResponseListener(mLinkedConnectionsProvider, mStationsProvider, request);
        mLinkedConnectionsProvider.getLinkedConnectionsByDate(request.getSearchTime(),
                                                              listener,
                                                              listener,
                                                              request.getTag());
    }

    @Override
    public void getRoutes(@NonNull IrailRoutesRequest... requests) {
        // TODO: switch to API specific code
        for (IrailRoutesRequest request :
                requests) {
            getRoutes(request);
        }
    }

    @Override
    public void extendRoutes(@NonNull ExtendRoutesRequest... requests) {
        for (ExtendRoutesRequest request :
                requests) {
            (new RouteAppendHelper()).extendRoutesRequest(request);
        }
    }

    private void getRoutes(@NonNull IrailRoutesRequest request) {

        DateTime departureLimit;

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            departureLimit = request.getSearchTime();
        } else {
            departureLimit = request.getSearchTime().minusHours(24);
        }

        RouteResponseListener listener = new RouteResponseListener(mLinkedConnectionsProvider, mStationsProvider, request, departureLimit);

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            mLinkedConnectionsProvider.getLinkedConnectionsByDateForTimeSpan(request.getSearchTime(), request.getSearchTime().plusHours(6), listener, listener, null);
        } else {
            mLinkedConnectionsProvider.getLinkedConnectionsByDateForTimeSpan(request.getSearchTime().minusHours(1), request.getSearchTime(), listener, listener, null);
        }
    }

    @Override
    public void getRoute(@NonNull IrailRouteRequest... requests) {
        for (IrailRouteRequest request :
                requests) {
            getRoute(request);
        }
    }

    private void getRoute(@NonNull IrailRouteRequest requests) {
        // TODO: implement
    }

    @Override
    public void getStop(@NonNull VehicleStopRequest... requests) {
        for (VehicleStopRequest request :
                requests) {
            getStop(request);
        }
    }

    private void getStop(@NonNull VehicleStopRequest requests) {
        // TODO: implement
    }

    @Override
    public void getVehicle(@NonNull IrailVehicleRequest... requests) {
        for (IrailVehicleRequest request :
                requests) {
            getVehicle(request);
        }
    }

    private void getVehicle(@NonNull final IrailVehicleRequest request) {
        Log.i(LOGTAG, "Loading train...");
        VehicleResponseListener listener = new VehicleResponseListener(request, mStationsProvider);
        mLinkedConnectionsProvider.getLinkedConnectionsByDateForTimeSpan(request.getSearchTime().withTimeAtStartOfDay().withHourOfDay(3), request.getSearchTime().withTimeAtStartOfDay().plusDays(1).withHourOfDay(3), listener, listener, null);
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

    public static String basename(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public static String uriToId(String uri) {
        return "BE.NMBS." + basename(uri);
    }

}
