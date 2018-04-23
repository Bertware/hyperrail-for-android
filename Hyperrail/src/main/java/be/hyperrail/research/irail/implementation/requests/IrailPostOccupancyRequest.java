/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.research.irail.implementation.requests;

import android.support.annotation.NonNull;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import be.hyperrail.research.irail.contracts.IrailRequest;
import be.hyperrail.research.irail.contracts.OccupancyLevel;

/**
 * A request to post occupancy data
 */
public class IrailPostOccupancyRequest extends IrailBaseRequest<Boolean> implements IrailRequest<Boolean> {

    @NonNull
    private final String departureSemanticId;

    @NonNull
    private final String stationSemanticId;

    @NonNull
    private final String vehicleSemanticId;

    @NonNull
    private final DateTime date;

    @NonNull
    private final OccupancyLevel occupancy;

    /**
     * Create a request  to post occupancy data
     */
    public IrailPostOccupancyRequest(@NonNull String departureSemanticId, @NonNull String stationSemanticId, @NonNull String vehicleSemanticId, @NonNull DateTime date, @NonNull OccupancyLevel occupancy) {

        this.departureSemanticId = departureSemanticId;
        this.stationSemanticId = stationSemanticId;
        this.vehicleSemanticId = vehicleSemanticId;
        this.date = date;
        this.occupancy = occupancy;
    }

    /**
     * Deserialize JSON for a request to post occupancy data
     */
    public IrailPostOccupancyRequest(@NonNull JSONObject jsonObject) throws JSONException {
        super(jsonObject);

        this.departureSemanticId = jsonObject.getString("departure_semantic_id");
        this.stationSemanticId = jsonObject.getString("station_semantic_id");
        this.vehicleSemanticId = jsonObject.getString("vehicle_semantic_id");
        this.date = new DateTime(jsonObject.getLong("date"));
        this.occupancy = OccupancyLevel.valueOf(jsonObject.getString("occupancy"));
    }

    @NonNull
    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject json = super.toJson();
        json.put("departure_semantic_id", getDepartureSemanticId());
        json.put("station_semantic_id", getStationSemanticId());
        json.put("vehicle_semantic_id", getVehicleSemanticId());
        json.put("date", getDate().getMillis());
        json.put("occupancy", getOccupancy().name());
        return json;
    }

    @Override
    public boolean equalsIgnoringTime(IrailRequest other) {
        // Time is essential for this request
        // Not supported
        return false;
    }


    @NonNull
    public String getDepartureSemanticId() {
        return departureSemanticId;
    }

    @NonNull
    public String getStationSemanticId() {
        return stationSemanticId;
    }

    @NonNull
    public String getVehicleSemanticId() {
        return vehicleSemanticId;
    }

    @NonNull
    public DateTime getDate() {
        return date;
    }

    @NonNull
    public OccupancyLevel getOccupancy() {
        return occupancy;
    }

    @Override
    public int compareTo(@NonNull IrailRequest o) {
        if (!(o instanceof IrailPostOccupancyRequest)) {
            return -1;
        }
        return getDate().compareTo(((IrailPostOccupancyRequest) o).getDate());
    }
}
