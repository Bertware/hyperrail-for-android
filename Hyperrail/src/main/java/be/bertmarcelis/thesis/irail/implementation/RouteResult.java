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

package be.bertmarcelis.thesis.irail.implementation;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import be.bertmarcelis.thesis.irail.contracts.PagedResource;
import be.bertmarcelis.thesis.irail.contracts.PagedResourceDescriptor;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.db.Station;

/**
 * Result of a route query. Includes the query, as parsed server-side.
 * This query information can be used to display which question the server replied to,
 * and can be used to detect incorrect parsed stations server-side (e.g. when searching a station)
 */
public class RouteResult implements Serializable, PagedResource {

    private final Station origin;
    private final Station destination;
    private final RouteTimeDefinition timeDefinition;
    private final DateTime mLastSearchTime;
    private Route[] mRoutes;
    private PagedResourceDescriptor mDescriptor;

    public RouteResult(Station origin, Station destination, DateTime searchTime, RouteTimeDefinition timeDefinition, Route[] routes) {
        this.destination = destination;
        this.mLastSearchTime = searchTime;
        this.origin = origin;
        this.mRoutes = routes;
        this.timeDefinition = timeDefinition;
    }

    public Station getOrigin() {
        return origin;
    }

    public Station getDestination() {
        return destination;
    }

    public RouteTimeDefinition getTimeDefinition() {
        return timeDefinition;
    }

    public DateTime getSearchTime() {
        return mLastSearchTime;
    }

    public Route[] getRoutes() {
        return mRoutes;
    }

    @Override
    public PagedResourceDescriptor getPagedResourceDescriptor() {
        return mDescriptor;
    }

    @Override
    public void setPageInfo(PagedResourceDescriptor descriptor) {
        mDescriptor = descriptor;
    }

    public RouteResult withRoutesAppended(RouteResult... other) {
        HashMap<String, Route> routesByUri = new HashMap<>();
        for (Route r :
                mRoutes) {
            routesByUri.put(getRouteId(r), r);
        }

        for (RouteResult results : other
                ) {
            for (Route r :
                    results.getRoutes()) {
                routesByUri.put(getRouteId(r), r);
            }
        }

        Route[] routes = new Route[routesByUri.size()];
        routes = routesByUri.values().toArray(routes);

        Arrays.sort(routes, new Comparator<Route>() {
            @Override
            public int compare(Route o1, Route o2) {
                return o1.getDepartureTime().compareTo(o2.getDepartureTime());
            }
        });

        return new RouteResult(origin, destination, mLastSearchTime, timeDefinition, routes);
    }

    private static String getRouteId(Route route) {
        StringBuilder id = new StringBuilder();
        for (RouteLeg l : route.getLegs()) {
            id.append(l.getDeparture().getUri());
        }
        return id.toString();
    }
}
