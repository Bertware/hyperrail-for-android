package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.JsonAttribute;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 15/03/2018.
 */

@CompiledJson(onUnknown = CompiledJson.Behavior.IGNORE)
public class LinkedConnections {
    @JsonAttribute(name = "@id")
    String current;
    @JsonAttribute(name = "hydra:previous")
    String previous;
    @JsonAttribute(name =  "hydra:next")
    String next;
    @JsonAttribute(name = "@graph")
    LinkedConnection[] connections;
}
