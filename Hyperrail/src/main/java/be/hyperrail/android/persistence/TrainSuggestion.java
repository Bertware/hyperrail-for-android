/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.android.persistence;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.TrainStub;

public class TrainSuggestion extends TrainStub implements Suggestable {

    Date created_at;

    TrainSuggestion() {
        super(null, null);
    }

    public TrainSuggestion(TrainStub trainStub) {
        super(null, null);
        if (trainStub != null) {
            this.id = trainStub.getId();
            this.direction = trainStub.getDirection();
        }
        this.created_at = new Date();
    }

    @Override
    public JSONObject serialize() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", this.id);
        json.put("created_at", created_at.getTime());
        if (direction != null) {
            json.put("direction", direction.getId());
        }
        return json;
    }

    @Override
    public void deserialize(JSONObject json) throws JSONException {
        this.created_at = new Date(json.getLong("created_at"));
        this.id = json.getString("id");
        if (json.has("direction")) {
            this.direction = IrailFactory.getStationsProviderInstance().getStationById(json.getString("direction"));
        }
    }

    @Override
    public String getSortingName() {
        return getId();
    }

    @Override
    public Date getSortingDate() {
        return created_at;
    }

    @Override
    public boolean equals(JSONObject json) throws JSONException {
        return (json.getString("id").equals(this.id));
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof TrainStub) {
            return ((TrainStub) o).getId().equals(this.getId());
        }
        return false;
    }
}