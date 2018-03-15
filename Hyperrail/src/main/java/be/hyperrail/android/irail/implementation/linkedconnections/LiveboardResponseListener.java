package be.hyperrail.android.irail.implementation.linkedconnections;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */

import android.support.annotation.NonNull;
import android.util.Log;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.OccupancyLevel;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.Liveboard;
import be.hyperrail.android.irail.implementation.VehicleStop;
import be.hyperrail.android.irail.implementation.VehicleStopType;
import be.hyperrail.android.irail.implementation.VehicleStub;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;

import static be.hyperrail.android.irail.implementation.LinkedConnectionsApi.basename;
import static be.hyperrail.android.irail.implementation.Liveboard.LiveboardType.ARRIVALS;

/**
 * A listener which receives graph.irail.be data and builds a liveboard for 2 hours.
 */
public class LiveboardResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {
    final ArrayList<LinkedConnection> arrivals = new ArrayList<>();
    final ArrayList<LinkedConnection> departures = new ArrayList<>();
    final ArrayList<VehicleStop> stops = new ArrayList<>();

    // Both departures and arrivals are in chronological order. We'll search to see if we can find a departure which matches an arrival, but only start looking AFTER this arrival.
    final ArrayList<Integer> departureIndexForArrivals = new ArrayList<>();
    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private final IrailStationProvider mStationProvider;
    private IrailLiveboardRequest request;

    public LiveboardResponseListener(LinkedConnectionsProvider linkedConnectionsProvider, IrailStationProvider stationProvider, IrailLiveboardRequest request) {
        mLinkedConnectionsProvider = linkedConnectionsProvider;
        mStationProvider = stationProvider;
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

        if (request.getType() == Liveboard.LiveboardType.DEPARTURES && departures.size() > 20 || request.getType() == ARRIVALS && arrivals.size() > 20) {
            VehicleStop[] stoparray = generateStopArray();
            request.notifySuccessListeners(new Liveboard(request.getStation(), stoparray, request.getSearchTime(), request.getType(), request.getTimeDefinition()));
        } else {
            String link = data.next;
            // When searching for "arrive before", we need to look backwards
            if (request.getTimeDefinition() == RouteTimeDefinition.ARRIVE_AT) {
                link = data.previous;
            }
            mLinkedConnectionsProvider.getLinkedConnectionByUrl(link,
                                     this,
                                     new IRailErrorResponseListener() {
                                         @Override
                                         public void onErrorResponse(@NonNull Exception e, Object tag) {
                                             Log.w("LiveboardResponseLstnr", "Getting next LC page failed");
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
                                              departure.getDelayedDepartureTime().isAfterNow(),
                                              departure.uri,
                                              OccupancyLevel.UNSUPPORTED,
                                              VehicleStopType.STOP));
                }
            }
        }

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            for (int i = 0; i < departures.size(); i++) {
                if (handledConnections.contains(departures.get(i))) {
                    continue;
                }

                LinkedConnection departure = departures.get(i);
                Station direction = mStationProvider.getStationByName(
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
                                          departure.getDelayedDepartureTime().isAfterNow(),
                                          departure.uri,
                                          OccupancyLevel.UNSUPPORTED,
                                          VehicleStopType.DEPARTURE));

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
                    continue;
                }
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
                                          arrival.getDelayedArrivalTime().isAfterNow(),
                                          arrival.uri,
                                          OccupancyLevel.UNSUPPORTED,
                                          VehicleStopType.ARRIVAL));

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