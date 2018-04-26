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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.joda.time.DateTime;

import java.io.FileNotFoundException;

import be.bertmarcelis.thesis.R;
import be.bertmarcelis.thesis.VehiclePopupContextMenu;
import be.bertmarcelis.thesis.activities.searchresult.VehicleActivity;
import be.bertmarcelis.thesis.adapter.LiveboardCardAdapter;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemClickListener;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemLongClickListener;
import be.bertmarcelis.thesis.infiniteScrolling.InfiniteScrollingDataSource;
import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.VehicleStop;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailVehicleRequest;
import be.bertmarcelis.thesis.util.ErrorDialogFactory;

/**
 * A fragment for showing liveboard results
 */
public class LiveboardFragment extends RecyclerViewFragment<Liveboard> implements InfiniteScrollingDataSource, ResultFragment<IrailLiveboardRequest>, OnRecyclerItemClickListener<VehicleStop>, OnRecyclerItemLongClickListener<VehicleStop> {

    private Liveboard mCurrentLiveboard;
    private LiveboardCardAdapter mLiveboardCardAdapter;
    private IrailLiveboardRequest mRequest;

    public static LiveboardFragment createInstance(IrailLiveboardRequest request) {
        // Clone the request so we can't accidentally modify the original
        LiveboardFragment frg = new LiveboardFragment();
        frg.mRequest = request;
        return frg;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("request")) {
            mRequest = (IrailLiveboardRequest) savedInstanceState.getSerializable("request");
        }
        return inflater.inflate(R.layout.fragment_recyclerview_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("request", mRequest);
        outState.putSerializable("result", mCurrentLiveboard);
    }

    @Override
    protected Liveboard getRestoredInstanceStateItems(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("result")) {
            mCurrentLiveboard = (Liveboard) savedInstanceState.getSerializable("result");
        }
        return mCurrentLiveboard;
    }

    @Override
    public void setRequest(@NonNull IrailLiveboardRequest request) {
        this.mRequest = request;
        getInitialData();
    }

    @Override
    public IrailLiveboardRequest getRequest() {
        return this.mRequest;
    }

    @Override
    public void onDateTimePicked(DateTime d) {
        mRequest.setSearchTime(d);
        getData();
    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        if (mLiveboardCardAdapter == null) {
            mLiveboardCardAdapter = new LiveboardCardAdapter(this.getActivity(), vRecyclerView, this);
            mLiveboardCardAdapter.setOnItemClickListener(this);
            mLiveboardCardAdapter.setOnItemLongClickListener(this);
        }
        return mLiveboardCardAdapter;
    }

    @Override
    protected void getInitialData() {
        getData();
    }

    @Override
    protected void getData() {

        mCurrentLiveboard = null;
        showData(null);

        if (this.vRefreshLayout.isRefreshing()) {
            // Disable infinite scrolling for now to prevent having 2 loading icons
            mLiveboardCardAdapter.setInfiniteScrolling(false);
        } else {
            // Restore if it was disabled earlier on. Will still be blocked since currentLiveboard == null
            mLiveboardCardAdapter.setInfiniteScrolling(true);
        }

        IrailDataProvider api = IrailFactory.getDataProviderInstance();
        // Don't abort all queries: there might be multiple fragments at the same screen!

        mRequest.setCallback(new IRailSuccessResponseListener<Liveboard>() {
            @Override
            public void onSuccessResponse(@NonNull final Liveboard data, Object tag) {
                vRefreshLayout.setRefreshing(false);

                // store retrieved data
                mCurrentLiveboard = data;
                // Show retrieved data
                showData(mCurrentLiveboard);

if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // If we didn't get a result, try the next data
                        if (data.getStops().length == 0) {
                            LiveboardFragment.this.loadNextRecyclerviewItems();
                        } else {
                            // Enable infinite scrolling again
                            mLiveboardCardAdapter.setInfiniteScrolling(true);
                        }

                        // Scroll past the load earlier item
                        ((LinearLayoutManager) vRecyclerView.getLayoutManager()).scrollToPositionWithOffset(1, 0);
                    }
                });
            }

        }, new IRailErrorResponseListener() {
            @Override
            public void onErrorResponse(@NonNull final Exception e, Object tag) {if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        vRefreshLayout.setRefreshing(false);
                        // only finish if we're loading new data
                        ErrorDialogFactory.showErrorDialog(e, LiveboardFragment.this.getActivity(), mCurrentLiveboard == null);
                    }
                });
            }
        }, null);
        api.getLiveboard(mRequest);
    }

    @Override
    public void loadNextRecyclerviewItems() {
        // When not yet initialized with the first data, don't load more
        // mCurrentLiveboard won't be empty after the first response: its stops array might be empty, but the object will be NonNull
        if (mCurrentLiveboard == null) {
            mLiveboardCardAdapter.setNextLoaded();
            return;
        }

        ExtendLiveboardRequest request = new ExtendLiveboardRequest(mCurrentLiveboard, ExtendLiveboardRequest.Action.APPEND);
        request.setCallback(new IRailSuccessResponseListener<Liveboard>() {
            @Override
            public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
                // Compare the new one with the old one to check if stops have been added
                if (data.getStops().length == mCurrentLiveboard.getStops().length) {
                    mLiveboardCardAdapter.disableInfiniteNext();
                }
                mCurrentLiveboard = data;
                showData(mCurrentLiveboard);

                mLiveboardCardAdapter.setNextLoaded();

                // Scroll past the "load earlier"
                if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LinearLayoutManager mgr = ((LinearLayoutManager) vRecyclerView.getLayoutManager());
                        if (mgr.findFirstVisibleItemPosition() == 0) {
                            mgr.scrollToPositionWithOffset(1, 0);
                        }
                    }
                });
            }

        }, new IRailErrorResponseListener() {
            @Override
            public void onErrorResponse(@NonNull final Exception e, Object tag) {if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ErrorDialogFactory.showErrorDialog(e, LiveboardFragment.this.getActivity(), false);
                        mLiveboardCardAdapter.setNextError(true);
                        mLiveboardCardAdapter.setNextLoaded();
                    }
                });
            }
        }, null);
        IrailFactory.getDataProviderInstance().extendLiveboard(request);
    }

    @Override
    public void loadPreviousRecyclerviewItems() {
        // When not yet initialized with the first data, don't load previous
        if (mCurrentLiveboard == null) {
            mLiveboardCardAdapter.setPrevLoaded();
            return;
        }

        ExtendLiveboardRequest request = new ExtendLiveboardRequest(mCurrentLiveboard, ExtendLiveboardRequest.Action.PREPEND);
        request.setCallback(new IRailSuccessResponseListener<Liveboard>() {
            @Override
            public void onSuccessResponse(@NonNull final Liveboard data, Object tag) {
                // Compare the new one with the old one to check if stops have been added
                if (data.getStops().length == mCurrentLiveboard.getStops().length) {
                    ErrorDialogFactory.showErrorDialog(new FileNotFoundException("No results"), getActivity(), false);
                    mLiveboardCardAdapter.disableInfinitePrevious();
                }if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LinearLayoutManager mgr = ((LinearLayoutManager) vRecyclerView.getLayoutManager());
                        if (mgr.findFirstVisibleItemPosition() == 0) {
                            int oldLength = mLiveboardCardAdapter.getItemCount();

                            mCurrentLiveboard = data;
                            showData(mCurrentLiveboard);

                            int newLength = mLiveboardCardAdapter.getItemCount();
                            // Scroll past the load earlier item
                            ((LinearLayoutManager) vRecyclerView.getLayoutManager()).scrollToPositionWithOffset(newLength - oldLength, 0);

                            mLiveboardCardAdapter.setPrevLoaded();
                        }
                    }
                });

            }
        }, new IRailErrorResponseListener() {
            @Override
            public void onErrorResponse(@NonNull final Exception e, Object tag) {if (getActivity() != null)
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ErrorDialogFactory.showErrorDialog(e, LiveboardFragment.this.getActivity(), false);
                        mLiveboardCardAdapter.setPrevLoaded();
                    }
                });

            }
        }, null);
        IrailFactory.getDataProviderInstance().extendLiveboard(request);
    }

    @Override
    protected void showData(Liveboard liveBoard) {
        mLiveboardCardAdapter.updateLiveboard(liveBoard);
    }


    @Override
    public void onRecyclerItemClick(RecyclerView.Adapter sender, VehicleStop object) {
        Intent i = VehicleActivity.createIntent(getActivity(), new IrailVehicleRequest(object.getVehicle().getId(), object.getDepartureTime()));
        startActivity(i);
    }

    @Override
    public void onRecyclerItemLongClick(RecyclerView.Adapter sender, VehicleStop object) {if (getActivity() != null)
        (new VehiclePopupContextMenu(getActivity(), object)).show();
    }

}
