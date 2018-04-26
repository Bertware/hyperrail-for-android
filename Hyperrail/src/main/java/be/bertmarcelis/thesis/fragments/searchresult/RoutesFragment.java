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


package be.bertmarcelis.thesis.fragments.searchresult;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.joda.time.DateTime;

import be.bertmarcelis.thesis.R;
import be.bertmarcelis.thesis.activities.searchresult.RouteDetailActivity;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemClickListener;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemLongClickListener;
import be.bertmarcelis.thesis.adapter.RouteCardAdapter;
import be.bertmarcelis.thesis.infiniteScrolling.InfiniteScrollingDataSource;
import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.Route;
import be.bertmarcelis.thesis.irail.implementation.RouteResult;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendRoutesRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailRoutesRequest;
import be.bertmarcelis.thesis.util.ErrorDialogFactory;

/**
 * A fragment for showing liveboard results
 */
public class RoutesFragment extends RecyclerViewFragment<RouteResult> implements InfiniteScrollingDataSource, ResultFragment<IrailRoutesRequest>, OnRecyclerItemClickListener<Route>, OnRecyclerItemLongClickListener<Route> {

    private RouteResult mCurrentRouteResult;
    private RouteCardAdapter mRouteCardAdapter;
    private IrailRoutesRequest mRequest;

    public static RoutesFragment createInstance(IrailRoutesRequest request) {
        RoutesFragment frg = new RoutesFragment();
        frg.mRequest = request;
        return frg;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("request")) {
            mRequest = (IrailRoutesRequest) savedInstanceState.getSerializable("request");
        }
        return inflater.inflate(R.layout.fragment_recyclerview_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void setRequest(@NonNull IrailRoutesRequest request) {
        this.mRequest = request;
        getInitialData();
    }

    @Override
    public IrailRoutesRequest getRequest() {
        return this.mRequest;
    }

    @Override
    public void onDateTimePicked(DateTime d) {
        mRequest.setSearchTime(d);
        getData();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("result", mCurrentRouteResult);
        outState.putSerializable("request", mRequest);
    }

    @Override
    protected RouteResult getRestoredInstanceStateItems(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("result")) {
            this.mCurrentRouteResult = (RouteResult) savedInstanceState.get("result");
        }
        return mCurrentRouteResult;
    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        if (mRouteCardAdapter == null) {
            mRouteCardAdapter = new RouteCardAdapter(getActivity(), vRecyclerView, this);
            mRouteCardAdapter.setOnItemClickListener(this);
            mRouteCardAdapter.setOnItemLongClickListener(this);
        }
        return mRouteCardAdapter;
    }

    @Override
    protected void getInitialData() {
        getData();
    }

    protected void getData() {
        Log.d("RouteActivity", "Get original data");

        // Clear the view
        showData(null);
        mCurrentRouteResult = null;

        // Restore infinite scrolling
        mRouteCardAdapter.setInfiniteScrolling(true);

        IrailDataProvider api = IrailFactory.getDataProviderInstance();
        api.abortAllQueries();

        IrailRoutesRequest request = new IrailRoutesRequest(mRequest.getOrigin(),
                                                            mRequest.getDestination(),
                                                            mRequest.getTimeDefinition(),
                                                            mRequest.getSearchTime());
        request.setCallback(new IRailSuccessResponseListener<RouteResult>() {
                                @Override
                                public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
                                    vRefreshLayout.setRefreshing(false);
                                    mCurrentRouteResult = data;

                                    final LinearLayoutManager mgr = ((LinearLayoutManager) vRecyclerView.getLayoutManager());
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                showData(mCurrentRouteResult);
                                                // Scroll past the load earlier item
                                                mgr.scrollToPositionWithOffset(1, 0);
                                            }
                                        });
                                    }
                                }
                            }, new IRailErrorResponseListener() {
                                @Override
                                public void onErrorResponse(@NonNull final Exception e, Object tag) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // only finish if we're loading new data
                                                mRouteCardAdapter.setInfiniteScrolling(false);
                                                ErrorDialogFactory.showErrorDialog(e, getActivity(), mCurrentRouteResult == null);
                                            }
                                        });
                                    }
                                }
                            },
                            null);

        api.getRoutes(request);
    }

    public void loadNextRecyclerviewItems() {
        if (mCurrentRouteResult == null) {
            mRouteCardAdapter.setNextLoaded();
            return;
        }

        ExtendRoutesRequest request = new ExtendRoutesRequest(mCurrentRouteResult, ExtendRoutesRequest.Action.APPEND);
        request.setCallback(
                new IRailSuccessResponseListener<RouteResult>() {
                    @Override
                    public void onSuccessResponse(@NonNull final RouteResult data, Object tag) {

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // data consists of both old and new routes

                                    if (data.getRoutes().length == mCurrentRouteResult.getRoutes().length) {
                                        mRouteCardAdapter.disableInfiniteNext(); // Nothing new anymore
                                        // ErrorDialogFactory.showErrorDialog(new FileNotFoundException("No results"), RouteActivity.this,  (mSearchDate == null));
                                    }

                                    mCurrentRouteResult = data;

                                    mRouteCardAdapter.setNextLoaded();

                                    // Scroll past the "load earlier"
                                    final LinearLayoutManager mgr = ((LinearLayoutManager) vRecyclerView.getLayoutManager());
                                    // Scroll past the load earlier item
                                    if (mgr.findFirstVisibleItemPosition() == 0) {
                                        mgr.scrollToPositionWithOffset(1, 0);
                                        showData(mCurrentRouteResult);
                                    }
                                }
                            });
                        }
                    }


                }, new IRailErrorResponseListener() {
                    @Override
                    public void onErrorResponse(@NonNull final Exception e, Object tag) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ErrorDialogFactory.showErrorDialog(e, getActivity(), false);
                                    mRouteCardAdapter.setNextError(true);
                                    mRouteCardAdapter.setNextLoaded();
                                }
                            });
                        }
                    }
                }, null);
        IrailFactory.getDataProviderInstance().extendRoutes(request);
    }

    public void loadPreviousRecyclerviewItems() {
        if (mCurrentRouteResult == null) {
            mRouteCardAdapter.setPrevLoaded();
            return;
        }

        ExtendRoutesRequest request = new ExtendRoutesRequest(mCurrentRouteResult, ExtendRoutesRequest.Action.PREPEND);
        request.setCallback(
                new IRailSuccessResponseListener<RouteResult>() {
                    @Override
                    public void onSuccessResponse(@NonNull final RouteResult data, Object tag) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // only finish if we're loading new data
                                    // data consists of both old and new routes
                                    if (data.getRoutes().length == mCurrentRouteResult.getRoutes().length) {
                                        // ErrorDialogFactory.showErrorDialog(new FileNotFoundException("No results"), RouteActivity.this,  (mSearchDate == null));
                                        mRouteCardAdapter.disableInfinitePrevious();
                                    }

                                    int oldLength = mRouteCardAdapter.getItemCount();

                                    mCurrentRouteResult = data;
                                    showData(mCurrentRouteResult);

                                    int newLength = mRouteCardAdapter.getItemCount();

                                    // Scroll past the load earlier item
                                    ((LinearLayoutManager) vRecyclerView.getLayoutManager()).scrollToPositionWithOffset(newLength - oldLength, 0);

                                    mRouteCardAdapter.setPrevLoaded();
                                }
                            });
                        }
                    }
                }, new IRailErrorResponseListener() {
                    @Override
                    public void onErrorResponse(@NonNull final Exception e, Object tag) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ErrorDialogFactory.showErrorDialog(e, getActivity(), false);
                                    mRouteCardAdapter.setPrevLoaded();
                                }
                            });
                        }
                    }
                }, null);
        IrailFactory.getDataProviderInstance().extendRoutes(request);
    }

    protected void showData(RouteResult routeList) {
        mRouteCardAdapter.updateRoutes(routeList);
    }

    @Override
    public void onRecyclerItemClick(RecyclerView.Adapter sender, Route object) {
        // Nothing to do, collapsing/expanding of items is handled by the adapter.
    }

    @Override
    public void onRecyclerItemLongClick(RecyclerView.Adapter sender, Route object) {
        Intent i = RouteDetailActivity.createIntent(getActivity(), object);
        this.startActivity(i);
    }

}
