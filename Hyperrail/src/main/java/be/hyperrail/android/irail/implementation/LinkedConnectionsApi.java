package be.hyperrail.android.irail.implementation;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import be.hyperrail.android.R;
import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.MeteredApi;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.irailapi.RouteAppendHelper;
import be.hyperrail.android.irail.implementation.linkedconnections.LinkedConnectionsProvider;
import be.hyperrail.android.irail.implementation.linkedconnections.LiveboardExtendHelper;
import be.hyperrail.android.irail.implementation.linkedconnections.LiveboardResponseListener;
import be.hyperrail.android.irail.implementation.linkedconnections.RouteResponseListener;
import be.hyperrail.android.irail.implementation.linkedconnections.VehicleQueryResponseListener;
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

import static be.hyperrail.android.irail.implementation.Liveboard.LiveboardType.ARRIVALS;
import static be.hyperrail.android.irail.implementation.Liveboard.LiveboardType.DEPARTURES;

/**
 * This API loads linkedConnection data and builds responses based on this data
 */
public class LinkedConnectionsApi implements IrailDataProvider, MeteredApi {

    private final IrailStationProvider mStationsProvider;
    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private Context mContext;
    private static final String LOGTAG = "LinkedConnectionsApi";
    List<MeteredRequest> mMeteredRequests = new ArrayList<>();

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

    private void getLiveboard(@NonNull final IrailLiveboardRequest request) {
        MeteredRequest meteredRequest = new MeteredRequest();
        meteredRequest.setTag(request.toString());
        meteredRequest.setMsecStart(DateTime.now().getMillis());
        mMeteredRequests.add(meteredRequest);

        LiveboardResponseListener listener = new LiveboardResponseListener(mLinkedConnectionsProvider, mStationsProvider, request);
        mLinkedConnectionsProvider.getLinkedConnectionsByDate(request.getSearchTime(),
                                                              listener,
                                                              listener,
                                                              meteredRequest);
    }

    @Override
    public void extendLiveboard(@NonNull ExtendLiveboardRequest... requests) {
        for (final ExtendLiveboardRequest request :
                requests) {
            extendLiveboard(request);
        }
    }

    private void extendLiveboard(@NonNull final ExtendLiveboardRequest request) {
        MeteredRequest meteredRequest = new MeteredRequest();
        meteredRequest.setTag(request.toString());
        meteredRequest.setMsecStart(DateTime.now().getMillis());
        mMeteredRequests.add(meteredRequest);

        LiveboardExtendHelper helper = new LiveboardExtendHelper(mLinkedConnectionsProvider, mStationsProvider, request, meteredRequest);
        helper.extend();
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
        MeteredRequest meteredRequest = new MeteredRequest();
        meteredRequest.setTag(request.toString());
        meteredRequest.setMsecStart(DateTime.now().getMillis());
        mMeteredRequests.add(meteredRequest);

        DateTime departureLimit;

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            departureLimit = request.getSearchTime();
        } else {
            departureLimit = request.getSearchTime().minusHours(24);
        }

        RouteResponseListener listener = new RouteResponseListener(mLinkedConnectionsProvider, mStationsProvider, request, departureLimit);

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            DateTime end = request.getSearchTime();
            if (end.getHourOfDay() < 18 && end.getHourOfDay() >= 6) {
                end = request.getSearchTime().plusHours(4);
            } else {
                end = request.getSearchTime().plusHours(6);
            }
            mLinkedConnectionsProvider.getLinkedConnectionsByDateForTimeSpan(request.getSearchTime(), end, listener, listener, meteredRequest);
        } else {
            mLinkedConnectionsProvider.getLinkedConnectionsByDateForTimeSpan(request.getSearchTime().minusHours(1), request.getSearchTime(), listener, listener, meteredRequest);
        }
    }

    @Override
    public void getRoute(@NonNull IrailRouteRequest... requests) {
        for (IrailRouteRequest request :
                requests) {
            getRoute(request);
        }
    }

    private void getRoute(@NonNull final IrailRouteRequest request) {
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

    @Override
    public void getStop(@NonNull VehicleStopRequest... requests) {
        for (VehicleStopRequest request :
                requests) {
            getStop(request);
        }
    }

    private void getStop(@NonNull final VehicleStopRequest request) {
        IrailLiveboardRequest liveboardRequest;
        if (request.getStop().getType() == VehicleStopType.DEPARTURE || request.getStop().getType() == VehicleStopType.STOP) {
            liveboardRequest = new IrailLiveboardRequest(request.getStop().getStation(), RouteTimeDefinition.DEPART_AT, DEPARTURES, request.getStop().getDepartureTime());
        } else {
            liveboardRequest = new IrailLiveboardRequest(request.getStop().getStation(), RouteTimeDefinition.ARRIVE_AT, ARRIVALS, request.getStop().getArrivalTime());
        }
        liveboardRequest.setCallback(new IRailSuccessResponseListener<Liveboard>() {
            @Override
            public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
                for (VehicleStop stop :
                        data.getStops()) {
                    if (stop.getDepartureUri().equals(request.getStop().getDepartureUri())) {
                        request.notifySuccessListeners(stop);
                        return;
                    }
                }
            }
        }, request.getOnErrorListener(), null);
        getLiveboard(liveboardRequest);
    }

    @Override
    public void getVehicle(@NonNull IrailVehicleRequest... requests) {
        for (IrailVehicleRequest request :
                requests) {
            getVehicle(request);
        }
    }

    private void getVehicle(@NonNull final IrailVehicleRequest request) {
        startVehicleRequestTask startVehicleRequestTask = new startVehicleRequestTask(this);
        startVehicleRequestTask.execute(request);
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

    @Override
    public MeteredRequest[] getMeteredRequests() {
        MeteredRequest[] meteredRequests = new MeteredRequest[mMeteredRequests.size()];
        return mMeteredRequests.toArray(meteredRequests);
    }

    static class startVehicleRequestTask extends AsyncTask<IrailVehicleRequest, Void, Void> {

        private final WeakReference<LinkedConnectionsApi> mApi;

        startVehicleRequestTask(LinkedConnectionsApi api){
            mApi = new WeakReference<>(api);
        }

        @Override
        protected Void doInBackground(IrailVehicleRequest... requests) {

            if (mApi.get() == null){
                return null;
            }
            LinkedConnectionsApi api = mApi.get();

            IrailVehicleRequest request = requests[0];
            MeteredRequest meteredRequest = new MeteredRequest();
            meteredRequest.setTag(request.toString());
            meteredRequest.setMsecStart(DateTime.now().getMillis());
            api.mMeteredRequests.add(meteredRequest);

            VehicleResponseListener listener = new VehicleResponseListener(request, api.mStationsProvider);
            VehicleQueryResponseListener query = new VehicleQueryResponseListener("http://irail.be/vehicle/" + request.getVehicleId(), listener, listener, meteredRequest);

            ArrayList<String> departures = new ArrayList<>();
            try (InputStream in = api.mContext.getResources().openRawResource(R.raw.firstdepartures)) {
                java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
                while (s.hasNextLine()) departures.add(s.nextLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
            DateTime departureTime = request.getSearchTime().withTimeAtStartOfDay().withHourOfDay(3);
            for (String departure : departures) {
                if (departure.split(" ")[0].equals(request.getVehicleId())) {
                    departureTime = request.getSearchTime().withTimeAtStartOfDay()
                            .withHourOfDay(Integer.valueOf(departure.split(" ")[1]))
                            .withMinuteOfHour(Integer.valueOf(departure.split(" ")[2]));
                    break;
                }
            }

            api.mLinkedConnectionsProvider.queryLinkedConnections(departureTime, query, meteredRequest);
            return null;
        }
    }


}
