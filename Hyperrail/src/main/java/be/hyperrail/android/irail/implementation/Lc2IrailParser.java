/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.android.irail.implementation;

import android.support.annotation.NonNull;

import com.android.volley.toolbox.JsonObjectRequest;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.OccupancyLevel;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRoutesRequest;
import be.hyperrail.android.irail.implementation.requests.IrailVehicleRequest;

/**
 * A simple parser for api.irail.be.
 *
 * @inheritDoc
 */
public class Lc2IrailParser {

    private final IrailStationProvider stationProvider;
    private DateTimeFormatter dtf = ISODateTimeFormat.dateTimeNoMillis();

    Lc2IrailParser(@NonNull IrailStationProvider stationProvider) {
        this.stationProvider = stationProvider;
    }

    @NonNull
    public Liveboard parseLiveboard(@NonNull IrailLiveboardRequest request, @NonNull JSONObject json) throws JSONException {
        List<VehicleStop> stops = new ArrayList<>();
        JSONArray jsonStops = json.getJSONArray("stops");
        for (int i = 0; i < jsonStops.length(); i++) {
            stops.add(parseLiveboardStop(request, jsonStops.getJSONObject(i)));
        }

        JSONArray departuresOrArrivals;
        if (request.getType() == Liveboard.LiveboardType.DEPARTURES) {
            departuresOrArrivals = json.getJSONArray("departures");
        } else {
            departuresOrArrivals = json.getJSONArray("arrivals");
        }
        for (int i = 0; i < departuresOrArrivals.length(); i++) {
            stops.add(parseLiveboardStop(request, departuresOrArrivals.getJSONObject(i)));
        }

        VehicleStop[] stopArray = new VehicleStop[stops.size()];
        stops.toArray(stopArray);
        Arrays.sort(stopArray, new Comparator<VehicleStop>() {
            @Override
            public int compare(VehicleStop o1, VehicleStop o2) {
                if (o1.getDepartureTime() != null && o2.getDepartureTime() != null) {
                    return o1.getDepartureTime().compareTo(o2.getDepartureTime());
                }
                if (o1.getArrivalTime() != null && o2.getArrivalTime() != null) {
                    return o1.getArrivalTime().compareTo(o2.getArrivalTime());
                }
                if (o1.getDepartureTime() != null && o2.getArrivalTime() != null) {
                    return o1.getDepartureTime().compareTo(o2.getArrivalTime());
                }
                return o1.getArrivalTime().compareTo(o2.getDepartureTime());
            }
        });
        return new Liveboard(request.getStation(), stopArray, request.getSearchTime(), request.getType(), request.getTimeDefinition());
    }

    @NonNull
    private VehicleStop parseLiveboardStop(@NonNull IrailLiveboardRequest request, @NonNull JSONObject json) throws JSONException {
        /*
          "arrivalDelay": 0,
          "arrivalTime": "2018-04-13T15:21:00+02:00",
          "departureDelay": 0,
          "departureTime": "2018-04-13T15:24:00+02:00",
          "platform": "0",
          "uri": "http://irail.be/connections/8841004/20180413/L5386",
          "vehicle": {
            "uri": "http://irail.be/vehicle/L5386/20180413",
            "id": "L5386",
            "direction": "Hasselt"
          }
         */

        int departureDelay = 0;
        int arrivalDelay = 0;
        DateTime departureTime = null;
        DateTime arrivalTime = null;
        String platform = "?";
        String uri = null;
        VehicleStub vehicle = null;
        boolean hasLeft = false;

        if (json.has("arrivalTime")) {
            arrivalTime = DateTime.parse(json.getString("arrivalTime"), dtf);
            arrivalDelay = json.getInt("arrivalDelay");
            if (arrivalTime.plusSeconds(arrivalDelay).isBeforeNow()) {
                hasLeft = true;
            }
        }
        if (json.has("departureTime")) {
            departureTime = DateTime.parse(json.getString("departureTime"), dtf);
            departureDelay = json.getInt("departureDelay");
            if (departureTime.plusSeconds(departureDelay).isBeforeNow()) {
                hasLeft = true;
            }
        }
        if (json.has("platform")) {
            platform = json.getString("platform");
        }
        if (json.has("uri")) {
            uri = json.getString("uri");
        }
        if (json.has("vehicle")) {
            vehicle = new VehicleStub(
                    json.getJSONObject("vehicle").getString("id"),
                    stationProvider.getStationByName(json.getJSONObject("vehicle").getString("direction")),
                    json.getJSONObject("vehicle").getString("uri")
            );
        }

        VehicleStopType type;
        if (departureTime != null) {
            if (arrivalTime != null) {
                type = VehicleStopType.STOP;
            } else {
                type = VehicleStopType.DEPARTURE;
            }
        } else {
            type = VehicleStopType.ARRIVAL;
        }

        return new VehicleStop(request.getStation(),
                               vehicle.direction,
                               vehicle,
                               platform,
                               true,
                               departureTime, arrivalTime, Duration.standardSeconds(departureDelay),
                               Duration.standardSeconds(arrivalDelay),
                               false,
                               false,
                               hasLeft,
                               uri,
                               OccupancyLevel.UNSUPPORTED,
                               type
        );

    }

    @NonNull
    public Vehicle parseVehicle(@NonNull IrailVehicleRequest request, @NonNull JSONObject response) throws JSONException {
        String id = response.getString("id");
        String uri = response.getString("uri");
        Station direction = stationProvider.getStationByName(response.getString("direction"));

        VehicleStub vehicleStub = new VehicleStub(id, direction, uri);
        JSONArray jsonStops = response.getJSONArray("stops");
        VehicleStop stops[] = new VehicleStop[jsonStops.length()];

        double latitude = 0;
        double longitude = 0;

        for (int i = 0; i < jsonStops.length(); i++) {
            VehicleStopType type = VehicleStopType.STOP;
            if (i == 0) {
                type = VehicleStopType.DEPARTURE;
            } else if (i == jsonStops.length() - 1) {
                type = VehicleStopType.ARRIVAL;
            }

            stops[i] = parseVehicleStop(request, jsonStops.getJSONObject(i), vehicleStub, type);

            if (i == 0 || stops[i].hasLeft()) {
                longitude = stops[i].getStation().getLongitude();
                latitude = stops[i].getStation().getLatitude();
            }
        }
        return new Vehicle(id, uri, direction, stops[0].getStation(), longitude, latitude, stops);
    }

    @NonNull
    private VehicleStop parseVehicleStop(@NonNull IrailVehicleRequest request, @NonNull JSONObject json, @NonNull VehicleStub vehicle, @NonNull VehicleStopType type) throws JSONException {
        /*
        {
          "departureDelay": 0,
          "departureTime": "2018-04-13T15:18:00+02:00",
          "platform": "0",
          "station": {
            "id": "BE.NMBS.008844628",
            "uri": "http://irail.be/stations/NMBS/008844628",
            "defaultName": "Eupen",
            "localizedName": "Eupen",
            "latitude": "50.635157",
            "longitude": "6.03711",
            "countryCode": "be",
            "countryURI": "http://sws.geonames.org/2802361/"
          },
          "uri": "http://irail.be/connections/8844628/20180413/IC538"
        },
        */

        int departureDelay = 0;
        int arrivalDelay = 0;
        DateTime departureTime = null;
        DateTime arrivalTime = null;
        String platform = "?";
        String uri = null;
        boolean hasLeft = false;

        Station station = stationProvider.getStationByUri(json.getJSONObject("station").getString("uri"));

        if (json.has("arrivalTime")) {
            arrivalTime = DateTime.parse(json.getString("arrivalTime"), dtf);
            arrivalDelay = json.getInt("arrivalDelay");
            if (arrivalTime.plusSeconds(arrivalDelay).isBeforeNow()) {
                hasLeft = true;
            }
        }

        if (json.has("departureTime")) {
            departureTime = DateTime.parse(json.getString("departureTime"), dtf);
            departureDelay = json.getInt("departureDelay");
            if (departureTime.plusSeconds(departureDelay).isBeforeNow()) {
                hasLeft = true;
            }
        }

        if (json.has("platform")) {
            platform = json.getString("platform");
        }

        if (json.has("uri")) {
            uri = json.getString("uri");
        }

        return new VehicleStop(station,
                               vehicle.direction,
                               vehicle,
                               platform,
                               true,
                               departureTime, arrivalTime, Duration.standardSeconds(departureDelay),
                               Duration.standardSeconds(arrivalDelay),
                               false,
                               false,
                               hasLeft,
                               uri,
                               OccupancyLevel.UNSUPPORTED,
                               type
        );
    }

    @NonNull
    public RouteResult parseRoutes(@NonNull IrailRoutesRequest request, @NonNull JSONObject json) throws JSONException {
        Station origin = stationProvider.getStationByUri(json.getJSONObject("station").getString("uri"));
        Station destination = stationProvider.getStationByUri(json.getJSONObject("station").getString("uri"));

        JSONArray connections = json.getJSONArray("connections");
        Route[] routes = new Route[connections.length()];
        for (int i = 0; i < connections.length(); i++) {
            routes[i] = parseConnection(request, connections.getJSONObject(i));
        }

        return new RouteResult(origin, destination, request.getSearchTime(), request.getTimeDefinition(), routes);
    }

    /**
     * Parse a connection existing of one or more legs
     *
     * @param request The request for routes used to become this response
     * @param json    The connection JSON object
     * @return The object representation of the passed JSON
     */
    @NonNull
    private Route parseConnection(@NonNull IrailRoutesRequest request, @NonNull JSONObject json) throws JSONException {
        /*
          "legs": [
            {
              "arrivalConnection": "http://irail.be/connections/8813003/20180415/IC541",
              "arrivalDelay": 0,
              "arrivalStation": {
                "id": "BE.NMBS.008814001",
                "uri": "http://irail.be/stations/NMBS/008814001",
                "defaultName": "Brussel-Zuid/Bruxelles-Midi",
                "localizedName": "Brussels-South/Brussels-Midi",
                "latitude": "50.835707",
                "longitude": "4.336531",
                "countryCode": "be",
                "countryURI": "http://sws.geonames.org/2802361/"
              },
              "arrivalTime": "2018-04-15T18:00:00+00:00",
              "departureConnection": "http://irail.be/connections/8841004/20180415/IC541",
              "departureDelay": 0,
              "departureStation": {
                "id": "BE.NMBS.008841004",
                "uri": "http://irail.be/stations/NMBS/008841004",
                "defaultName": "Liège-Guillemins",
                "localizedName": "Liège-Guillemins",
                "latitude": "50.62455",
                "longitude": "5.566695",
                "countryCode": "be",
                "countryURI": "http://sws.geonames.org/2802361/"
              },
              "departureTime": "2018-04-15T17:01:00+00:00",
              "direction": "Ostende",
              "route": "IC541",
              "trip": "http://irail.be/vehicle/IC541/20180415"
            }
          ],
          "departureTime": "2018-04-15T17:01:00+00:00",
          "arrivalTime": "2018-04-15T18:00:00+00:00"
        },
         */
        JSONArray jsonlegs = json.getJSONArray("legs");
        RouteLeg[] legs = new RouteLeg[jsonlegs.length()];
        for (int i = 0; i < jsonlegs.length(); i++) {
            JSONObject jsonLeg = jsonlegs.getJSONObject(i);
            VehicleStub vehicle = new VehicleStub(
                    jsonLeg.getJSONObject("vehicle").getString("id"),
                    stationProvider.getStationByName(jsonLeg.getJSONObject("vehicle").getString("direction")),
                    jsonLeg.getJSONObject("vehicle").getString("uri")
            );

            Station departureStation = stationProvider.getStationByUri(jsonLeg.getJSONObject("departureStation").getString("uri"));
            Station arrivalStation = stationProvider.getStationByUri(jsonLeg.getJSONObject("arrivalStation").getString("uri"));

            if (departureStation == null || arrivalStation == null) {
                throw new IllegalArgumentException("Invalid connection JSON! " + jsonLeg.toString());
            }

            DateTime departureTime = DateTime.parse(jsonLeg.getString("departureTime"), dtf);
            int departureDelay = jsonLeg.getInt("departureDelay");
            RouteLegEnd departure = new RouteLegEnd(departureStation,
                                                    departureTime,
                                                    "?",
                                                    true,
                                                    Duration.standardSeconds(departureDelay),
                                                    false,
                                                    departureTime.plusSeconds(departureDelay).isBeforeNow(),
                                                    jsonLeg.getString("departureUri"),
                                                    OccupancyLevel.UNSUPPORTED);
            DateTime arrivalTime = DateTime.parse(jsonLeg.getString("arrivalTime"), dtf);
            int arrivalDelay = jsonLeg.getInt("arrivalDelay");
            RouteLegEnd arrival = new RouteLegEnd(arrivalStation,
                                                  arrivalTime,
                                                  "?",
                                                  true,
                                                  Duration.standardSeconds(arrivalDelay),
                                                  false,
                                                  arrivalTime.plusSeconds(arrivalDelay).isBeforeNow(),
                                                  jsonLeg.getString("arrivalUri"),
                                                  OccupancyLevel.UNSUPPORTED);

            legs[i] = new RouteLeg(RouteLegType.TRAIN, vehicle, departure, arrival);
        }
        return new Route(legs);
    }
}
