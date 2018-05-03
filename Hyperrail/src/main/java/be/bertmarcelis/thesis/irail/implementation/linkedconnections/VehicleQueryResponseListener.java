package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import com.google.firebase.perf.metrics.AddTrace;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;

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

    private DateTime started;
    private DateTime lastSpotted;

    public VehicleQueryResponseListener(String vehicleUri, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {
        mVehicleUri = vehicleUri;
        mSuccessListener = successListener;
        mErrorListener = errorListener;
        mTag = tag;
    }

    @Override
    @AddTrace(name="vehicleQuery.result")
    public int onQueryResult(LinkedConnections data) {
        if (current == null) {
            previous = data.previous;
            current = data.current;
        }

        if (data.connections.length < 1) {
            return 1;
        }

        if (started == null){
            started = data.connections[0].getDepartureTime();
        } else {
            if (new Duration(started,data.connections[0].getDepartureTime()).getStandardHours() > 24){
                mErrorListener.onErrorResponse(new FileNotFoundException(),mTag);
                return 0;
            }
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
