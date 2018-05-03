package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.perf.metrics.AddTrace;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi.MeteredRequest;
import be.bertmarcelis.thesis.irail.contracts.OccupancyLevel;
import be.bertmarcelis.thesis.irail.contracts.PagedResourceDescriptor;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.db.Station;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.VehicleStop;
import be.bertmarcelis.thesis.irail.implementation.VehicleStopType;
import be.bertmarcelis.thesis.irail.implementation.VehicleStub;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;

import static be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi.basename;
import static be.bertmarcelis.thesis.irail.implementation.Liveboard.LiveboardType.ARRIVALS;
import static be.bertmarcelis.thesis.irail.implementation.Liveboard.LiveboardType.DEPARTURES;

/**
 * A listener which receives graph.irail.be data and builds a liveboard for 2 hours.
 */
public class LiveboardResponseListener implements IRailSuccessResponseListener<LinkedConnections>, IRailErrorResponseListener {
    private final ArrayList<LinkedConnection> arrivals = new ArrayList<>();
    private final ArrayList<LinkedConnection> departures = new ArrayList<>();
    private final ArrayList<VehicleStop> stops = new ArrayList<>();

    // Both departures and arrivals are in chronological order. We'll search to see if we can find a departure which matches an arrival, but only start looking AFTER this arrival.
    private final ArrayList<Integer> departureIndexForArrivals = new ArrayList<>();
    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private final IrailStationProvider mStationProvider;
    private IrailLiveboardRequest request;

    private String previous;
    private String current;
    private String next;

    public LiveboardResponseListener(LinkedConnectionsProvider linkedConnectionsProvider, IrailStationProvider stationProvider, IrailLiveboardRequest request) {
        mLinkedConnectionsProvider = linkedConnectionsProvider;
        mStationProvider = stationProvider;
        this.request = request;
    }

    @Override
    @AddTrace(name = "LiveboardResponseListener.onSuccess")
    public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {

        ((MeteredRequest) tag).setMsecUsableNetworkResponse(DateTime.now().getMillis());

        if (current == null) {
            previous = data.previous;
            current = data.current;
            next = data.next;
        }

        if (request.getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            // Moving forward through pages
            next = data.next;
        } else {
            // Moving backward through pages
            previous = data.previous;
        }

        for (LinkedConnection connection : data.connections) {
            if (connection.getDepartureStationUri().equals(request.getStation().getUri())) {
                departures.add(connection);
            }
            if (connection.getArrivalStationUri().equals(request.getStation().getUri())) {
                arrivals.add(connection);
                departureIndexForArrivals.add(departures.size());
            }
        }


        if (request.getType() == Liveboard.LiveboardType.DEPARTURES && departures.size() > 0 || request.getType() == ARRIVALS && arrivals.size() > 0) {
            VehicleStop[] stoparray = generateStopArray();
            Liveboard liveboard = new Liveboard(request.getStation(), stoparray, request.getSearchTime(), request.getType(), request.getTimeDefinition());
            liveboard.setPageInfo(new PagedResourceDescriptor(previous, current, next));
            request.notifySuccessListeners(liveboard);
            ((MeteredRequest) tag).setMsecParsed(DateTime.now().getMillis());
        } else {
            String link = data.next;
            // When searching for "arrive before", we need to look backwards
            if (request.getTimeDefinition() == RouteTimeDefinition.ARRIVE_AT) {
                link = data.previous;
            }

            if (data.connections.length > 0 && data.connections[0].getDepartureTime().isAfter(request.getSearchTime().plusHours(24))) {
                request.notifyErrorListeners(new FileNotFoundException());
                return;
            }

            mLinkedConnectionsProvider.getLinkedConnectionsByUrl(link,
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
        ((MeteredRequest) tag).setMsecParsed(DateTime.now().getMillis());
        ((MeteredApi.MeteredRequest) tag).setResponseType(MeteredApi.RESPONSE_FAILED);
    }

    @AddTrace(name = "LiveboardResponseListener.createStopArray")
    private VehicleStop[] generateStopArray() {
        // Find stops (train arrives and leaves again)
        ArrayList<LinkedConnection> handledConnections = new ArrayList<>();

        for (int i = 0; i < arrivals.size(); i++) {
            boolean foundMatchingDeparture = false;

            for (int j = departureIndexForArrivals.get(i); j < departures.size() && !foundMatchingDeparture; j++) {
                if (Objects.equals(arrivals.get(i).getTrip(), departures.get(j).getTrip())) {
                    foundMatchingDeparture = true;

                    LinkedConnection departure = departures.get(j);
                    LinkedConnection arrival = arrivals.get(i);

                    handledConnections.add(departure);
                    handledConnections.add(arrival);

                    Station direction = IrailFactory.getStationsProviderInstance().getStationByName(
                            departure.getDirection());

                    String headsign;
                    if (direction == null) {
                        headsign = departure.getDirection();
                    } else {
                        headsign = direction.getLocalizedName();
                    }
                    stops.add(new VehicleStop(request.getStation(),
                                              new VehicleStub(
                                                      basename(departure.getRoute()),
                                                      headsign,
                                                      departure.getRoute()),
                                              "?",
                                              true,
                                              departure.getDepartureTime(),
                                              arrival.getArrivalTime(),
                                              Duration.standardSeconds(departure.getDepartureDelay()),
                                              Duration.standardSeconds(arrival.getArrivalDelay()),
                                              false,
                                              false,
                                              departure.getDelayedDepartureTime().isAfterNow(),
                                              departure.getUri(),
                                              OccupancyLevel.UNSUPPORTED,
                                              VehicleStopType.STOP));
                }
            }
        }

        if (request.getType() == DEPARTURES) {
            for (int i = 0; i < departures.size(); i++) {
                if (handledConnections.contains(departures.get(i))) {
                    continue;
                }

                LinkedConnection departure = departures.get(i);
                Station direction = mStationProvider.getStationByName(
                        departure.getDirection());
                String headsign;
                if (direction == null) {
                    headsign = departure.getDirection();
                } else {
                    headsign = direction.getLocalizedName();
                }
                stops.add(new VehicleStop(request.getStation(), new VehicleStub(
                        basename(departure.getRoute()),
                        headsign,
                        departure.getRoute()),
                                          "?",
                                          true,
                                          departure.getDepartureTime(),
                                          null,
                                          Duration.standardSeconds(departure.getDepartureDelay()),
                                          null,
                                          false,
                                          false,
                                          departure.getDelayedDepartureTime().isBeforeNow(),
                                          departure.getUri(),
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
                if (handledConnections.contains(arrivals.get(i))) {
                    continue;
                }
                LinkedConnection arrival = arrivals.get(i);
                Station direction = request.getStation();

                stops.add(new VehicleStop(request.getStation(), new VehicleStub(
                        basename(arrival.getRoute()),
                        direction.getLocalizedName(),
                        arrival.getRoute()),
                                          "?",
                                          true,
                                          null,
                                          arrival.getArrivalTime(),
                                          null,
                                          Duration.standardSeconds(arrival.getArrivalDelay()),
                                          false,
                                          false,
                                          arrival.getDelayedArrivalTime().isBeforeNow(),
                                          arrival.getUri(),
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