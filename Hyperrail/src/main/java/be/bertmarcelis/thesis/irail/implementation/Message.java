/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.bertmarcelis.thesis.irail.implementation;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * An alert or remark message
 */
public class Message implements Serializable{
    private String header;
    private String description;
    private String lead;
    private String link;

    public Message(JSONObject json){
        try {
            this.header = json.getString("header");
            this.description = json.getString("description");
            if (json.has("lead")) {
                this.lead = json.getString("lead");
            }
            if (json.has("link")) {
                this.link = json.getString("link");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getHeader() {
        return header;
    }

    public String getDescription() {
        return description;
    }

    public String getLead() {
        return lead;
    }

    public String getLink() {
        return link;
    }
}
