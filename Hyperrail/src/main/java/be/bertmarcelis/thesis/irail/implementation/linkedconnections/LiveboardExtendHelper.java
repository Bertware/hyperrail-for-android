package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import android.support.annotation.NonNull;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;
import be.bertmarcelis.thesis.irail.contracts.PagedResourceDescriptor;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 17/04/2018.
 */
public class LiveboardExtendHelper implements IRailSuccessResponseListener<Liveboard>, IRailErrorResponseListener {

    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private final IrailStationProvider mStationProvider;
    private final ExtendLiveboardRequest mRequest;
    private final MeteredApi.MeteredRequest mMeteredRequest;
    private Liveboard mLiveboard;

    public LiveboardExtendHelper(LinkedConnectionsProvider linkedConnectionsProvider, IrailStationProvider stationProvider, ExtendLiveboardRequest request, MeteredApi.MeteredRequest meteredRequest) {
        mLinkedConnectionsProvider = linkedConnectionsProvider;
        mStationProvider = stationProvider;
        mRequest = request;
        mMeteredRequest = meteredRequest;
    }

    public void extend() {
        extend(mRequest.getLiveboard());
    }

    private void extend(Liveboard liveboard) {
        mLiveboard = liveboard;
        String url;

        if (mRequest.getAction() == ExtendLiveboardRequest.Action.PREPEND) {
            url = (String) mLiveboard.getPagedResourceDescriptor().getPreviousPointer();
        } else {
            url = (String) mLiveboard.getPagedResourceDescriptor().getNextPointer();
        }

        final IrailLiveboardRequest liveboardRequest = new IrailLiveboardRequest(mLiveboard,
                                                                                 mLiveboard.getTimeDefinition(),
                                                                                 mLiveboard.getLiveboardType(),
                                                                                 mLiveboard.getSearchTime());

        liveboardRequest.setCallback(this, this, mMeteredRequest);
        LiveboardResponseListener listener = new LiveboardResponseListener(mLinkedConnectionsProvider, mStationProvider, liveboardRequest);

        mLinkedConnectionsProvider.getLinkedConnectionByUrl(url,
                                                            listener,
                                                            listener,
                                                            mMeteredRequest);

    }

    @Override
    public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
        int originalLength = mLiveboard.getStops().length;
        mLiveboard = mLiveboard.withStopsAppended(data);

        String previous = (String) mRequest.getLiveboard().getPagedResourceDescriptor().getPreviousPointer();
        String current = (String) mRequest.getLiveboard().getPagedResourceDescriptor().getPreviousPointer();
        String next = (String) mRequest.getLiveboard().getPagedResourceDescriptor().getPreviousPointer();

        if (mRequest.getAction() == ExtendLiveboardRequest.Action.APPEND) {
            next = (String) data.getPagedResourceDescriptor().getNextPointer();
        } else {
            previous = (String) data.getPagedResourceDescriptor().getPreviousPointer();
            current = (String) data.getPagedResourceDescriptor().getCurrentPointer();
        }
        mLiveboard.setPageInfo(new PagedResourceDescriptor(previous, current, next));

        if (mLiveboard.getStops().length == originalLength) {
            // Didn't find anything new
            extend(mLiveboard);
        } else {
            mRequest.notifySuccessListeners(mLiveboard);
        }
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        mRequest.notifyErrorListeners(e);
    }
}
