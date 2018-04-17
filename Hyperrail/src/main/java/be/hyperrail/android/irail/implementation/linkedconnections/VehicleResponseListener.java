package be.hyperrail.android.irail.implementation.linkedconnections;

import android.support.annotation.NonNull;
import android.util.Log;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.OccupancyLevel;
import be.hyperrail.android.irail.contracts.StationNotResolvedException;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.Vehicle;
import be.hyperrail.android.irail.implementation.VehicleStop;
import be.hyperrail.android.irail.implementation.VehicleStopType;
import be.hyperrail.android.irail.implementation.VehicleStub;
import be.hyperrail.android.irail.implementation.requests.IrailVehicleRequest;

import static be.hyperrail.android.irail.implementation.LinkedConnectionsApi.basename;

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

        List<VehicleStop> stops = new ArrayList<>();
        Log.i("VehicleResponseListener", "Parsing train...");
        LinkedConnection lastConnection = null;
        for (int i = 0; i < data.connections.length; i++) {
            LinkedConnection connection = data.connections[i];
            if (!Objects.equals(connection.route, "http://irail.be/vehicle/" + Vehicle.getVehicleClass(mRequest.getVehicleId()) + Vehicle.getVehicleNumber(mRequest.getVehicleId()))) {
                continue;
            }

            Station departure;

            try {
                departure = mStationProvider.getStationByUri(connection.departureStationUri);
            } catch (StationNotResolvedException e) {
                mRequest.notifyErrorListeners(e);
                return;
            }

            Station direction = mStationProvider.getStationByName(connection.direction);

            if (stops.size() == 0) {
                // First stop
                stops.add(VehicleStop.buildDepartureVehicleStop(departure, direction, new VehicleStub(basename(connection.route), connection.direction, connection.route), "?", true,
                                                                connection.departureTime,
                                                                Duration.standardSeconds(connection.departureDelay),
                                                                false, connection.getDelayedDepartureTime().isAfterNow(),
                                                                connection.uri, OccupancyLevel.UNSUPPORTED));
            } else {
                // Some stop during the journey
                assert lastConnection != null;
                stops.add(new VehicleStop(departure, direction, new VehicleStub(basename(connection.route), connection.direction, connection.route), "?", true,
                                          connection.departureTime, lastConnection.arrivalTime,
                                          Duration.standardSeconds(connection.departureDelay),
                                          Duration.standardSeconds(lastConnection.arrivalDelay),
                                          false, false, lastConnection.getDelayedArrivalTime().isAfterNow(),
                                          connection.uri, OccupancyLevel.UNSUPPORTED, VehicleStopType.STOP));
            }

            lastConnection = connection;
        }

        if (stops.size() > 0 && lastConnection != null) {
            Station arrival;
            try {
                arrival = IrailFactory.getStationsProviderInstance().getStationByUri(lastConnection.arrivalStationUri);
            } catch (StationNotResolvedException e) {
                mRequest.notifyErrorListeners(e);
                return;
            }

            Station direction = IrailFactory.getStationsProviderInstance().getStationByName(lastConnection.direction);

            // Arrival stop
            stops.add(VehicleStop.buildArrivalVehicleStop(arrival, direction, new VehicleStub(basename(lastConnection.route), lastConnection.direction, lastConnection.route),
                                                          "?", true,
                                                          lastConnection.arrivalTime,
                                                          Duration.standardSeconds(lastConnection.arrivalDelay),
                                                          false, lastConnection.getDelayedArrivalTime().isAfterNow(),
                                                          lastConnection.uri, OccupancyLevel.UNSUPPORTED));

            VehicleStop[] stopsArray = new VehicleStop[stops.size()];
            mRequest.notifySuccessListeners(new Vehicle(stops.get(0).getVehicle().getId(), lastConnection.route, 0, 0, stops.toArray(stopsArray)));
        }
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        Log.w("VehicleResponseListener", "Failed to load page! " + e.getMessage());
    }
}

