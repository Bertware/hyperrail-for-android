package be.bertmarcelis.thesis.irail.implementation.linkedconnections;

import android.support.annotation.NonNull;

import org.joda.time.DateTime;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;
import be.bertmarcelis.thesis.irail.contracts.PagedResourceDescriptor;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.implementation.Route;
import be.bertmarcelis.thesis.irail.implementation.RouteResult;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendRoutesRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailRoutesRequest;
import be.bertmarcelis.thesis.util.ArrayUtils;

/**
 * Created in be.hyperrail.android.irail.implementation.linkedconnections on 17/04/2018.
 */
public class RouteExtendHelper implements IRailSuccessResponseListener<RouteResult>, IRailErrorResponseListener {

    private final LinkedConnectionsProvider mLinkedConnectionsProvider;
    private final IrailStationProvider mStationProvider;
    private final ExtendRoutesRequest mRequest;
    private final MeteredApi.MeteredRequest mMeteredRequest;
    private RouteResult mRoutes;

    public RouteExtendHelper(LinkedConnectionsProvider linkedConnectionsProvider, IrailStationProvider stationProvider, ExtendRoutesRequest request, MeteredApi.MeteredRequest meteredRequest) {
        mLinkedConnectionsProvider = linkedConnectionsProvider;
        mStationProvider = stationProvider;
        mRequest = request;
        mMeteredRequest = meteredRequest;
    }

    public void extend() {
        extend(mRequest.getRoutes());
    }

    private void extend(RouteResult routes) {
        mRoutes = routes;
        String start = null, stop = null;
        DateTime departureLimit;
        if (mRequest.getRoutes().getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            departureLimit = mRequest.getRoutes().getSearchTime();
            if (mRequest.getAction() == ExtendRoutesRequest.Action.PREPEND) {
                start = (String) mRequest.getRoutes().getPagedResourceDescriptor().getPreviousPointer();
                stop = (String) mRequest.getRoutes().getPagedResourceDescriptor().getNextPointer();
            } else {
                start = (String) mRequest.getRoutes().getPagedResourceDescriptor().getCurrentPointer();
                stop = (String) mRequest.getRoutes().getPagedResourceDescriptor().getNextPointer();
            }
        } else {
            departureLimit = null;
            if (mRequest.getAction() == ExtendRoutesRequest.Action.PREPEND) {
                start = (String) mRequest.getRoutes().getPagedResourceDescriptor().getPreviousPointer();
            } else {
                start = (String) mRequest.getRoutes().getPagedResourceDescriptor().getNextPointer();
            }
        }


        final IrailRoutesRequest routesRequest = new IrailRoutesRequest(mRoutes.getOrigin(),
                                                                        mRoutes.getDestination(),
                                                                        mRoutes.getTimeDefinition(),
                                                                        mRoutes.getSearchTime());

        routesRequest.setCallback(this, this, mMeteredRequest);

        RouteResponseListener listener;
        if (mRequest.getAction() == ExtendRoutesRequest.Action.PREPEND) {
            listener = new RouteResponseListener(mLinkedConnectionsProvider, mStationProvider, routesRequest, null);
        } else {
            listener = new RouteResponseListener(mLinkedConnectionsProvider, mStationProvider, routesRequest, departureLimit);
        }

        if (mRequest.getRoutes().getTimeDefinition() == RouteTimeDefinition.DEPART_AT) {
            mLinkedConnectionsProvider.getLinkedConnectionsByUrlSpan(start, stop,
                                                                     listener,
                                                                     listener,
                                                                     mMeteredRequest);
        } else {
            mLinkedConnectionsProvider.getLinkedConnectionsByUrl(start,
                                                                 listener,
                                                                 listener,
                                                                 mMeteredRequest);
        }
    }

    @Override
    public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
        RouteResult appended = mRoutes.withRoutesAppended(data);
        appended.setPageInfo(new PagedResourceDescriptor(
                data.getPagedResourceDescriptor().getPreviousPointer(),
                data.getPagedResourceDescriptor().getCurrentPointer(),
                data.getPagedResourceDescriptor().getNextPointer()
        ));
        if (mRequest.getAction() == ExtendRoutesRequest.Action.APPEND) {
            if (appended.getRoutes().length > mRoutes.getRoutes().length) {
                mRequest.notifySuccessListeners(appended);
            } else {
                mRequest.getRoutes().setPageInfo(new PagedResourceDescriptor(
                        mRequest.getRoutes().getPagedResourceDescriptor().getPreviousPointer(),
                        mRequest.getRoutes().getPagedResourceDescriptor().getCurrentPointer(),
                        data.getPagedResourceDescriptor().getNextPointer()
                ));
                extend();
            }
        } else {
            if (appended.getRoutes().length > mRoutes.getRoutes().length) {
                mRequest.notifySuccessListeners(appended);
            } else {
                mRequest.getRoutes().setPageInfo(new PagedResourceDescriptor(
                        data.getPagedResourceDescriptor().getPreviousPointer(),
                        data.getPagedResourceDescriptor().getCurrentPointer(),
                        mRequest.getRoutes().getPagedResourceDescriptor().getNextPointer()
                ));
                extend();
            }
        }
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        mRequest.notifyErrorListeners(e);
    }
}
