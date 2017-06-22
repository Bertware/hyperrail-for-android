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

package be.hyperrail.android.irail.contracts;

import java.util.Date;

import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.implementation.Disturbance;
import be.hyperrail.android.irail.implementation.LiveBoard;
import be.hyperrail.android.irail.implementation.RouteResult;
import be.hyperrail.android.irail.implementation.Train;

/**
 * Retrieve (realtime) data according from the iRail API, or any API which has identical endpoints.
 * See http://docs.irail.be/
 */
public interface IrailDataProvider {

    IrailDataResponse<RouteResult> getRoute(Station from, Station to);

    IrailDataResponse<RouteResult> getRoute(Station from, Station to, Date timeFilter);

    IrailDataResponse<RouteResult> getRoute(Station from, Station to, Date timeFilter, RouteTimeDefinition timeFilterType);

    IrailDataResponse<RouteResult> getRoute(String from, String to);

    IrailDataResponse<RouteResult> getRoute(String from, String to, Date timeFilter);

    IrailDataResponse<RouteResult> getRoute(String from, String to, Date timeFilter, RouteTimeDefinition timeFilterType);

    IrailDataResponse<LiveBoard> getLiveboard(String name);

    IrailDataResponse<LiveBoard> getLiveboard(String name, Date timeFilter);

    IrailDataResponse<LiveBoard> getLiveboard(String name, Date timeFilter, RouteTimeDefinition timeFilterType);

    IrailDataResponse<Train> getTrain(String id);

    IrailDataResponse<Train> getTrain(String id, Date day);

    IrailDataResponse<Disturbance[]> getDisturbances();
}
