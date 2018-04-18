package be.hyperrail.android.irail.implementation.linkedconnections;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import be.hyperrail.android.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.android.irail.contracts.IRailSuccessResponseListener;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 18/04/2018.
 */
public class TimespanQueryResponseListener implements QueryResponseListener.LinkedConnectionsQuery {
    private final DateTime mEndTime;
    private final IRailSuccessResponseListener<LinkedConnections> mSuccessListener;
    private final IRailErrorResponseListener mErrorListener;
    private final Object mTag;

    private List<LinkedConnection> result = new ArrayList<>();
    private String previous, current;

    TimespanQueryResponseListener(final DateTime endTime, final IRailSuccessResponseListener<LinkedConnections> successListener, final IRailErrorResponseListener errorListener, Object tag) {
        mEndTime = endTime;
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

        Collections.addAll(result, data.connections);

        if (data.connections[data.connections.length - 1].getDepartureTime().isBefore(mEndTime)) {
            return 1;
        } else {
            LinkedConnections resultObject = new LinkedConnections();
            LinkedConnection[] connections = new LinkedConnection[result.size()];
            connections = result.toArray(connections);
            resultObject.connections = connections;
            resultObject.current = current;
            resultObject.previous = previous;
            resultObject.next = data.next;
            mSuccessListener.onSuccessResponse(resultObject, mTag);
            return 0;
        }
    }

    @Override
    public void onQueryFailed(Exception e, Object tag) {
        mErrorListener.onErrorResponse(e, tag);
    }
}
