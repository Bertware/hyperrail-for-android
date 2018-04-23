package be.hyperrail.research.irail.implementation.linkedconnections;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import be.hyperrail.research.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.research.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.research.irail.contracts.MeteredApi;

/**
 * A query for linkedconnections related to a certain vehicle. Stop after the vehicle hasn't been seen for 2 hours, only return relevant connections.
 */
public class VehicleQueryResponseListener implements QueryResponseListener.LinkedConnectionsQuery {
    private final String mVehicleUri;
    private final IRailSuccessResponseListener<LinkedConnections> mSuccessListener;
    private final IRailErrorResponseListener mErrorListener;
    private final Object mTag;

    private List<LinkedConnection> result = new ArrayList<>();
    private String previous, current;

    private DateTime lastSpotted;

    public VehicleQueryResponseListener(String vehicleUri, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {
        mVehicleUri = vehicleUri;
        mSuccessListener = successListener;
        mErrorListener = errorListener;
        mTag = tag;
    }

    @Override
    public int onQueryResult(LinkedConnections data) {
        if (current == null) {
            previous = data.previous;
            current = data.current;
        }

        if (data.connections.length < 1) {
            return 1;
        }

        DateTime lastDepartureTime = null;
        for (LinkedConnection connection : data.connections) {
            if (Objects.equals(connection.getRoute(), mVehicleUri)) {
                lastSpotted = connection.getArrivalTime();
                result.add(connection);
            }
            lastDepartureTime = connection.getDepartureTime();
        }

        if (lastSpotted != null && lastDepartureTime.isAfter(lastSpotted.plusHours(2))) {
            LinkedConnections resultObject = new LinkedConnections();
            LinkedConnection[] connections = new LinkedConnection[result.size()];
            connections = result.toArray(connections);
            resultObject.connections = connections;
            resultObject.current = current;
            resultObject.previous = previous;
            resultObject.next = data.next;
            mSuccessListener.onSuccessResponse(resultObject, mTag);
            ((MeteredApi.MeteredRequest) mTag).setMsecParsed(DateTime.now().getMillis());
            return 0;
        }

        return 1;
    }

    @Override
    public void onQueryFailed(Exception e, Object tag) {
        mErrorListener.onErrorResponse(e, tag);
        ((MeteredApi.MeteredRequest) tag).setMsecParsed(DateTime.now().getMillis());
        ((MeteredApi.MeteredRequest) tag).setResponseType(MeteredApi.RESPONSE_FAILED);
    }
}
