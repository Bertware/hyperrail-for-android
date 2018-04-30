package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import android.support.annotation.NonNull;
import android.util.Log;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;
import be.bertmarcelis.thesis.irail.contracts.OccupancyLevel;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;
import be.bertmarcelis.thesis.irail.db.Station;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.Vehicle;
import be.bertmarcelis.thesis.irail.implementation.VehicleStop;
import be.bertmarcelis.thesis.irail.implementation.VehicleStopType;
import be.bertmarcelis.thesis.irail.implementation.VehicleStub;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailVehicleRequest;

import static be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi.basename;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */

public class VehicleResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {

    private IrailVehicleRequest mRequest;
    private final IrailStationProvider mStationProvider;

    public VehicleResponseListener(IrailVehicleRequest request, IrailStationProvider stationProvider) {
        mRequest = request;
        mStationProvider = stationProvider;
    }

    @Override
    public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {
        ((MeteredApi.MeteredRequest)tag).setMsecUsableNetworkResponse(DateTime.now().getMillis());
        List<VehicleStop> stops = new ArrayList<>();
        Log.i("VehicleResponseListener", "Parsing train...");
        LinkedConnection lastConnection = null;
        for (int i = 0; i < data.connections.length; i++) {
            LinkedConnection connection = data.connections[i];
            if (!Objects.equals(connection.getRoute(), "http://irail.be/vehicle/" + mRequest.getVehicleId())) {
                continue;
            }

            Station departure;

            try {
                departure = mStationProvider.getStationByUri(connection.getDepartureStationUri());
            } catch (StationNotResolvedException e) {
                mRequest.notifyErrorListeners(e);
                return;
            }

            Station direction = mStationProvider.getStationByName(connection.getDirection());
            String headsign;
            if (direction != null) {
                headsign = direction.getLocalizedName();
            } else {
                headsign = connection.getDirection();
            }
            if (stops.size() == 0) {
                // First stop
                stops.add(VehicleStop.buildDepartureVehicleStop(departure, new VehicleStub(basename(connection.getRoute()), headsign, connection.getRoute()), "?", true,
                                                                connection.getDepartureTime(),
                                                                Duration.standardSeconds(connection.getDepartureDelay()),
                                                                false, connection.getDelayedDepartureTime().isBeforeNow(),
                                                                connection.getUri(), OccupancyLevel.UNSUPPORTED));
            } else {
                // Some stop during the journey
                assert lastConnection != null;
                stops.add(new VehicleStop(departure, new VehicleStub(basename(connection.getRoute()), headsign, connection.getRoute()), "?", true,
                                          connection.getDepartureTime(), lastConnection.getArrivalTime(),
                                          Duration.standardSeconds(connection.getDepartureDelay()),
                                          Duration.standardSeconds(lastConnection.getArrivalDelay()),
                                          false, false, lastConnection.getDelayedArrivalTime().isBeforeNow(),
                                          connection.getUri(), OccupancyLevel.UNSUPPORTED, VehicleStopType.STOP));
            }

            lastConnection = connection;
        }

        if (stops.size() > 0 && lastConnection != null) {
            Station arrival;
            try {
                arrival = IrailFactory.getStationsProviderInstance().getStationByUri(lastConnection.getArrivalStationUri());
            } catch (StationNotResolvedException e) {
                mRequest.notifyErrorListeners(e);
                return;
            }

            Station direction = IrailFactory.getStationsProviderInstance().getStationByName(lastConnection.getDirection());
            String headsign;
            if (direction != null) {
                headsign = direction.getLocalizedName();
            } else {
                headsign = lastConnection.getDirection();
            }
            // Arrival stop
            stops.add(VehicleStop.buildArrivalVehicleStop(arrival, new VehicleStub(basename(lastConnection.getRoute()), headsign, lastConnection.getRoute()),
                                                          "?", true,
                                                          lastConnection.getArrivalTime(),
                                                          Duration.standardSeconds(lastConnection.getArrivalDelay()),
                                                          false, lastConnection.getDelayedArrivalTime().isBeforeNow(),
                                                          lastConnection.getUri(), OccupancyLevel.UNSUPPORTED));

            VehicleStop[] stopsArray = new VehicleStop[stops.size()];
            ((MeteredApi.MeteredRequest)tag).setMsecParsed(DateTime.now().getMillis());
            mRequest.notifySuccessListeners(new Vehicle(stops.get(0).getVehicle().getId(), lastConnection.getRoute(), 0, 0, stops.toArray(stopsArray)));
        }
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        Log.w("VehicleResponseListener", "Failed to load page! " + e.getMessage());
        mRequest.notifyErrorListeners(e);
    }
}

