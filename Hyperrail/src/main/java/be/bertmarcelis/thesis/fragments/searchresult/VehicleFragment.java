/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.bertmarcelis.thesis.fragments.searchresult;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import be.bertmarcelis.thesis.R;
import be.bertmarcelis.thesis.VehiclePopupContextMenu;
import be.bertmarcelis.thesis.activities.searchresult.LiveboardActivity;
import be.bertmarcelis.thesis.activities.searchresult.VehicleActivity;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemClickListener;
import be.bertmarcelis.thesis.adapter.OnRecyclerItemLongClickListener;
import be.bertmarcelis.thesis.adapter.VehicleStopCardAdapter;
import be.bertmarcelis.thesis.infiniteScrolling.InfiniteScrollingDataSource;
import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.db.Station;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.Vehicle;
import be.bertmarcelis.thesis.irail.implementation.VehicleStop;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailVehicleRequest;
import be.bertmarcelis.thesis.persistence.PersistentQueryProvider;
import be.bertmarcelis.thesis.persistence.Suggestion;
import be.bertmarcelis.thesis.persistence.SuggestionType;
import be.bertmarcelis.thesis.util.ErrorDialogFactory;

/**
 * A fragment for showing liveboard results
 */
public class VehicleFragment extends RecyclerViewFragment<Vehicle> implements InfiniteScrollingDataSource, ResultFragment<IrailVehicleRequest>, OnRecyclerItemClickListener<VehicleStop>, OnRecyclerItemLongClickListener<VehicleStop>, OnMapReadyCallback {

    private Vehicle mCurrentTrain;
    private IrailVehicleRequest mRequest;
    private VehicleStopCardAdapter mRecyclerviewAdapter;
    private GoogleMap mMap;

    public static VehicleFragment createInstance(IrailVehicleRequest request) {
        VehicleFragment frg = new VehicleFragment();
        frg.mRequest = request;
        return frg;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("request")) {
            mRequest = (IrailVehicleRequest) savedInstanceState.getSerializable("request");
        }
        return inflater.inflate(R.layout.fragment_recyclerview_list_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("trains_map", true)) {
            SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        } else {
            getChildFragmentManager().findFragmentById(R.id.map).getView().setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("request", mRequest);
        outState.putSerializable("result", mCurrentTrain);
    }

    @Override
    protected Vehicle getRestoredInstanceStateItems(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("result")) {
            this.mCurrentTrain = (Vehicle) savedInstanceState.get("result");
        }
        return mCurrentTrain;
    }

    @Override
    public void setRequest(@NonNull IrailVehicleRequest request) {
        this.mRequest = request;
        //getInitialData();
    }

    @Override
    public IrailVehicleRequest getRequest() {
        return this.mRequest;
    }

    @Override
    public void onDateTimePicked(DateTime d) {
        mRequest.setSearchTime(d);
        getData();
    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        if (mRecyclerviewAdapter == null) {
            mRecyclerviewAdapter = new VehicleStopCardAdapter(getActivity(), null);
        }
        mRecyclerviewAdapter.setOnItemClickListener(this);
        mRecyclerviewAdapter.setOnItemLongClickListener(this);
        return mRecyclerviewAdapter;
    }

    @Override
    protected void getInitialData() {
        getData();
    }

    protected void getData() {
        vRefreshLayout.setRefreshing(true);

        IrailFactory.getDataProviderInstance().abortAllQueries();

        IrailVehicleRequest request = new IrailVehicleRequest(mRequest.getVehicleId(),
                                                              mRequest.getSearchTime());
        request.setCallback(new IRailSuccessResponseListener<Vehicle>() {
            @Override
            public void onSuccessResponse(@NonNull Vehicle data, Object tag) {

                mCurrentTrain = data;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            vRefreshLayout.setRefreshing(false);
                            showData(mCurrentTrain);
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
                            vRefreshLayout.setRefreshing(false);
                            // only finish if we're loading new data
                            ErrorDialogFactory.showErrorDialog(e, getActivity(), mCurrentTrain == null);
                        }
                    });
                }

            }
        }, null);
        IrailFactory.getDataProviderInstance().getVehicle(request);
    }

    protected void showData(Vehicle train) {
        if (getActivity() != null) {
            getActivity().setTitle(train.getName() + " " + train.getHeadsign());
        }

        mRecyclerviewAdapter.updateTrain(train);
        mRequest.setOrigin(train.getStops()[0].getStation());
        mRequest.setDirection(train.getDirection());

        // Update the request in the activity, so additional information will be stored when marking it as favorite
        if (getActivity() instanceof VehicleActivity) {
            ((VehicleActivity) getActivity()).setRequest(mRequest);
        }

        PersistentQueryProvider.getInstance(getActivity()).store(
                new Suggestion<>(mRequest, SuggestionType.HISTORY));

        if (!mRequest.isNow()) {
            int i = train.getStopnumberForDepartureTime(mRequest.getSearchTime());
            if (i >= 0) {
                vRecyclerView.scrollToPosition(i);
            }
        }
        if (mMap != null) {
            onMapReady(mMap);
        }
    }

    @Override
    public void loadNextRecyclerviewItems() {
        // Not supported
    }

    @Override
    public void loadPreviousRecyclerviewItems() {
        // Not supported
    }

    @Override
    public void onRecyclerItemClick(RecyclerView.Adapter sender, VehicleStop object) {
        // TODO: VehicleStop objects should have a way to distinguish the first and last stop
        DateTime queryTime = object.getArrivalTime();
        if (queryTime == null) {
            queryTime = object.getDepartureTime();
        }
        Intent i = LiveboardActivity.createIntent(getActivity(),
                                                  new IrailLiveboardRequest(object.getStation(),
                                                                            RouteTimeDefinition.DEPART_AT,
                                                                            Liveboard.LiveboardType.DEPARTURES,
                                                                            queryTime));
        startActivity(i);
    }

    @Override
    public void onRecyclerItemLongClick(RecyclerView.Adapter sender, VehicleStop stop) {
        if (getActivity() != null) {
            (new VehiclePopupContextMenu(getActivity(), stop)).show();
        }
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        mMap = map;

        if (mCurrentTrain == null) {
            return;
        }

        final LatLng locations[] = new LatLng[mCurrentTrain.getStops().length];
        final List<LatLng> passedLocations = new ArrayList<>();
        final List<LatLng> futureLocations = new ArrayList<>();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        BitmapDescriptor colorIcon = getMarkerIconFromDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.timeline_dot));
        BitmapDescriptor greyIcon = getMarkerIconFromDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.timeline_dot_muted));

        for (int i = 0; i < mCurrentTrain.getStops().length; i++) {
            Station s = mCurrentTrain.getStops()[i].getStation();
            if (s != null) {
                locations[i] = new LatLng(s.getLatitude(), s.getLongitude());
                if (mCurrentTrain.getStops()[i].hasLeft()) {
                    passedLocations.add(locations[i]);
                    map.addMarker(new MarkerOptions().position(locations[i]).title(s.getLocalizedName()).icon(colorIcon).anchor(0.5f, 0.5f));
                } else {
                    futureLocations.add(locations[i]);
                    map.addMarker(new MarkerOptions().position(locations[i]).title(s.getLocalizedName()).icon(greyIcon).anchor(0.5f, 0.5f));
                }
                builder.include(locations[i]);
            }
        }

        final LatLngBounds bounds = builder.build();

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                map.addPolyline(new PolylineOptions()
                                        .add(passedLocations.toArray(new LatLng[passedLocations.size()]))
                                        .color(getActivity().getResources().getColor(R.color.colorPrimary))
                                        .geodesic(false)
                                        .clickable(false)
                                        .jointType(JointType.DEFAULT)
                );
                map.addPolyline(new PolylineOptions()
                                        .add(futureLocations.toArray(new LatLng[futureLocations.size()]))
                                        .color(getActivity().getResources().getColor(R.color.colorMuted))
                                        .geodesic(false)
                                        .clickable(false)
                                        .jointType(JointType.DEFAULT)
                );

                if (passedLocations.size() > 0 && futureLocations.size() > 0) {
                    map.addPolyline(new PolylineOptions()
                                            .add(passedLocations.get(passedLocations.size() - 1))
                                            .add(futureLocations.get(0))
                                            .color(getActivity().getResources().getColor(R.color.colorPrimary))
                                            .geodesic(false)
                                            .clickable(false)
                                            .jointType(JointType.DEFAULT));
                }
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
                map.setBuildingsEnabled(true);
                map.setTrafficEnabled(false);
                map.setMinZoomPreference(7);
                map.setMaxZoomPreference(14);
                map.setLatLngBoundsForCameraTarget(bounds);
                if (ActivityCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    map.setMyLocationEnabled(true);
                }
            }
        });

    }


    private BitmapDescriptor getMarkerIconFromDrawable(Drawable drawable) {
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitmap, bitmap.getHeight() / 2, bitmap.getWidth() / 2, true));
    }
}
