package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import android.support.annotation.FontRes;
import android.support.annotation.Nullable;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 18/04/2018.
 */
public class UrlSpanQueryResponseListener implements QueryResponseListener.LinkedConnectionsQuery {
    private final String mLastUrl;
    private final IRailSuccessResponseListener<LinkedConnections> mSuccessListener;
    private final IRailErrorResponseListener mErrorListener;
    private final Object mTag;

    private List<LinkedConnection> result = new ArrayList<>();
    private String previous, current;

    UrlSpanQueryResponseListener(final String lastUrl,  @Nullable final IRailSuccessResponseListener<LinkedConnections> successListener, @Nullable final IRailErrorResponseListener errorListener, @Nullable Object tag) {
        mLastUrl = lastUrl;
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

        if ( mLastUrl.compareTo(data.current) > 0) {
            return 1;
        } else {
            LinkedConnections resultObject = new LinkedConnections();
            LinkedConnection[] connections = new LinkedConnection[result.size()];
            connections = result.toArray(connections);
            resultObject.connections = connections;
            resultObject.current = current;
            resultObject.previous = previous;
            resultObject.next = data.next;
            if (mSuccessListener != null) {
                mSuccessListener.onSuccessResponse(resultObject, mTag);
            }
            return 0;
        }
    }

    @Override
    public void onQueryFailed(Exception e, Object tag) {
        if (mErrorListener != null) {
            mErrorListener.onErrorResponse(e, tag);
        }
    }
}
