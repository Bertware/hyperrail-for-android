package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */

@JsonObject
public class LinkedConnections {
    @JsonField(name = "@id")
    String current;
    @JsonField(name = "hydra:previous")
    String previous;
    @JsonField(name = "hydra:next")
    String next;
    @JsonField(name = "@graph")
    LinkedConnection[] connections;
}
