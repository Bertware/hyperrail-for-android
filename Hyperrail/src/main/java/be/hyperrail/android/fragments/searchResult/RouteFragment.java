/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.android.fragments.searchResult;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.joda.time.DateTime;

import be.hyperrail.android.R;
import be.hyperrail.android.activities.searchResult.LiveboardActivity;
import be.hyperrail.android.activities.searchResult.TrainActivity;
import be.hyperrail.android.adapter.OnRecyclerItemClickListener;
import be.hyperrail.android.adapter.RouteDetailCardAdapter;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.implementation.Route;
import be.hyperrail.android.irail.implementation.TrainStop;
import be.hyperrail.android.irail.implementation.TrainStub;
import be.hyperrail.android.irail.implementation.Transfer;
import be.hyperrail.android.irail.implementation.requests.IrailLiveboardRequest;
import be.hyperrail.android.irail.implementation.requests.IrailRouteRequest;
import be.hyperrail.android.irail.implementation.requests.IrailTrainRequest;

/**
 * A fragment for showing liveboard results
 */
public class RouteFragment extends RecyclerViewFragment<Route> implements ResultFragment<IrailRouteRequest>, OnRecyclerItemClickListener<TrainStop>{

    private IrailRouteRequest mRequest;
    /**
     * The route to show
     */
    private Route mRoute;

    public static RouteFragment createInstance(Route r) {
        RouteFragment f = new RouteFragment();
        f.mRoute = r;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recyclerview_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.mShowDividers = false;
    }

    @Override
    public void setRequest(@NonNull IrailRouteRequest request) {
        this.mRequest = request;
    }

    @Override
    public IrailRouteRequest getRequest() {
        return this.mRequest;
    }

    @Override
    public void onDateTimePicked(DateTime d) {
        // Not supported
    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        RouteDetailCardAdapter adapter = new RouteDetailCardAdapter(getActivity(), mRoute, false);

        // Launch intents to view details / click through
        adapter.setOnItemClickListener(new OnRecyclerItemClickListener<Object>() {
            @Override
            public void onRecyclerItemClick(RecyclerView.Adapter sender, Object object) {
                Intent i = null;
                if (object instanceof Bundle) {
                    i = TrainActivity.createIntent(getActivity(),
                            new IrailTrainRequest(
                                    ((TrainStub) ((Bundle) object).getSerializable("train")).getId(),
                                    (DateTime) ((Bundle) object).getSerializable("date")
                            )
                    );


                } else if (object instanceof Transfer) {
                    i = LiveboardActivity.createIntent(getActivity(), new IrailLiveboardRequest(((Transfer) object).getStation(), RouteTimeDefinition.DEPART, null));
                }
                startActivity(i);
            }
        });
        return adapter;
    }

    @Override
    protected void getData() {
        // Refresh
        // TODO: implement
    }

    @Override
    protected void getInitialData() {
        // Initial data is already passed on to this fragment
    }

    @Override
    protected void showData(Route data) {
        // Not supported, already showing data by setting route on create
    }


    @Override
    public void onRecyclerItemClick(RecyclerView.Adapter sender, TrainStop object) {
        Intent i = TrainActivity.createIntent(getActivity().getApplicationContext(), new IrailTrainRequest(object.getTrain().getId(), object.getDepartureTime()));
        startActivity(i);
    }

    @Override
    public void loadNextRecyclerviewItems() {
        // Not supported
    }

    @Override
    public void loadPreviousRecyclerviewItems() {
        // Not supported
    }
}
