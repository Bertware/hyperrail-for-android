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

package be.bertmarcelis.thesis.irail.implementation.irailapi;

import android.support.annotation.NonNull;

import org.joda.time.DateTime;

import java.util.Arrays;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.VehicleStop;
import be.bertmarcelis.thesis.irail.implementation.VehicleStopType;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;

/**
 * A class which allows to withStopsAppended liveboards.
 */
public class LiveboardAppendHelper implements IRailSuccessResponseListener<Liveboard>, IRailErrorResponseListener {

    private final int TAG_APPEND = 0;
    private final int TAG_PREPEND = 1;

    private int attempt = 0;
    private DateTime lastSearchTime;
    private Liveboard originalLiveboard;
    private ExtendLiveboardRequest mExtendRequest;

    private IrailDataProvider api;

    public LiveboardAppendHelper(IrailDataProvider api) {
        this.api = api;
    }

    public void extendLiveboard(@NonNull ExtendLiveboardRequest extendRequest) {
        switch (extendRequest.getAction()) {
            default:
            case APPEND:
                appendLiveboard(extendRequest);
                break;
            case PREPEND:
                prependLiveboard(extendRequest);
                break;
        }
    }

    private void appendLiveboard(@NonNull ExtendLiveboardRequest extendRequest) {

        this.originalLiveboard = extendRequest.getLiveboard();
        mExtendRequest = extendRequest;

        if (originalLiveboard.getStops().length > 0) {
            if (originalLiveboard.getStops()[originalLiveboard.getStops().length - 1].getType() == VehicleStopType.DEPARTURE) {
                this.lastSearchTime = originalLiveboard.getStops()[originalLiveboard.getStops().length - 1].getDepartureTime();
            } else {
                this.lastSearchTime = originalLiveboard.getStops()[originalLiveboard.getStops().length - 1].getArrivalTime();
            }
        } else {
            this.lastSearchTime = originalLiveboard.getSearchTime().plusHours(1);
        }

        IrailLiveboardRequest request = new IrailLiveboardRequest(originalLiveboard, RouteTimeDefinition.DEPART_AT, originalLiveboard.getLiveboardType(), lastSearchTime);
        request.setCallback(this, this, TAG_APPEND);
        api.getLiveboard(request);
    }

    private void prependLiveboard(@NonNull ExtendLiveboardRequest extendRequest) {
        this.originalLiveboard = extendRequest.getLiveboard();
        mExtendRequest = extendRequest;

        if (originalLiveboard.getStops().length > 0) {
            if (originalLiveboard.getStops()[originalLiveboard.getStops().length - 1].getType() == VehicleStopType.DEPARTURE) {
                this.lastSearchTime = originalLiveboard.getStops()[0].getDepartureTime().minusHours(1);
            } else {
                this.lastSearchTime = originalLiveboard.getStops()[0].getArrivalTime().minusHours(1);
            }
        } else {
            this.lastSearchTime = originalLiveboard.getSearchTime().minusHours(1);
        }
        IrailLiveboardRequest request = new IrailLiveboardRequest(originalLiveboard, RouteTimeDefinition.ARRIVE_AT, originalLiveboard.getLiveboardType(), lastSearchTime);
        request.setCallback(this, this, TAG_PREPEND);
        api.getLiveboard(request);
    }

    @Override
    public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
        switch ((int) tag) {
            case TAG_APPEND:
                handleAppendSuccessResponse(data);
                break;
            case TAG_PREPEND:
                handlePrependSuccessResponse(data);
                break;
        }
    }

    /**
     * Handle a successful response when prepending a liveboard
     *
     * @param data The newly received data
     */
    private void handlePrependSuccessResponse(@NonNull Liveboard data) {
        // If there is new data
        if (data.getStops().length > 0) {
            mExtendRequest.notifySuccessListeners(originalLiveboard.withStopsAppended(data));
        } else {
            attempt++;
            lastSearchTime = lastSearchTime.minusHours(1);

            if (attempt < 12) {
                IrailLiveboardRequest request = new IrailLiveboardRequest(originalLiveboard, RouteTimeDefinition.ARRIVE_AT, originalLiveboard.getLiveboardType(), lastSearchTime);
                request.setCallback(this, this, TAG_PREPEND);
                api.getLiveboard(request);
            } else {
                mExtendRequest.notifySuccessListeners(originalLiveboard);
            }
        }
    }

    /**
     * Handle a successful response when appending a liveboard
     *
     * @param data The newly received data
     */
    private void handleAppendSuccessResponse(@NonNull Liveboard data) {
        int originalLength = originalLiveboard.getStops().length;
        Liveboard result = originalLiveboard.withStopsAppended(data);

        if (result.getStops().length> originalLength) {
            mExtendRequest.notifySuccessListeners(originalLiveboard.withStopsAppended(data));
        } else {
            // No results, search two hours further in case this day doesn't have results.
            // Skip 2 hours at once, possible due to large API pages.
            attempt++;
            lastSearchTime = lastSearchTime.plusHours(2);

            if (attempt < 12) {
                IrailLiveboardRequest request = new IrailLiveboardRequest(originalLiveboard, RouteTimeDefinition.DEPART_AT, originalLiveboard.getLiveboardType(), lastSearchTime);
                request.setCallback(this, this, TAG_APPEND);
                api.getLiveboard(request);
            } else {
                mExtendRequest.notifySuccessListeners(originalLiveboard);
            }
        }
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        mExtendRequest.notifyErrorListeners(e);
    }
}
