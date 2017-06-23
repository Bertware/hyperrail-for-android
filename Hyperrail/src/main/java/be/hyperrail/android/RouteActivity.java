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

package be.hyperrail.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import be.hyperrail.android.adapter.RouteCardAdapter;
import be.hyperrail.android.infiniteScrolling.InfiniteScrollingDataSource;
import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailDataResponse;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.factories.IrailFactory;
import be.hyperrail.android.irail.implementation.Route;
import be.hyperrail.android.irail.implementation.RouteResult;
import be.hyperrail.android.util.ErrorDialogFactory;
import be.hyperrail.android.util.OnDateTimeSetListener;

public class RouteActivity extends RecyclerViewActivity<Route[]> implements InfiniteScrollingDataSource, OnDateTimeSetListener {

    private RouteResult mRoutes;

    private Station mSearchFrom;
    private Station mSearchTo;
    private RouteTimeDefinition mSearchTimeType = RouteTimeDefinition.DEPART;
    private Date mSearchDate;

    private AsyncTask runningTask;

    private boolean initialLoadCompleted = false;

    public static Intent createIntent(Context context, Station from, Station to, Date date, RouteTimeDefinition datetype) {
        Intent i = new Intent(context, RouteActivity.class);
        i.putExtra("from", from);
        i.putExtra("to", to);
        if (date != null) {
            i.putExtra("date", date);
        }
        i.putExtra("arrivedepart", datetype.name());
        return i;
    }

    public static Intent createIntent(Context context, Station from, Station to, Date date) {
        Intent i = new Intent(context, RouteActivity.class);
        i.putExtra("from", from);
        i.putExtra("to", to);
        if (date != null) {
            i.putExtra("date", date);
        }
        i.putExtra("arrivedepart", RouteTimeDefinition.DEPART);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey("routes")) {
            this.mRoutes = (RouteResult) savedInstanceState.get("routes");
        }

        super.onCreate(savedInstanceState);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("routes", mRoutes);
    }

    @Override
    protected Route[] getRestoredInstanceStateItems() {
        if (mRoutes == null) {
            return null;
        } else {
            return mRoutes.getRoutes();
        }
    }

    @Override
    protected void getInitialData() {

        if (mRoutes != null) {
            // routes are already retrieved from instance state (e.g. on rotation)
            showData(mRoutes.getRoutes());
            return;
        }

        Bundle mSearchArgs = getIntent().getExtras();

        mSearchFrom = (Station) mSearchArgs.getSerializable("from");
        mSearchTo = (Station) mSearchArgs.getSerializable("to");
        mSearchTimeType = RouteTimeDefinition.valueOf(mSearchArgs.getString("arrivedepart"));

        if (mSearchArgs.containsKey("date")) {
            mSearchDate = (Date) mSearchArgs.getSerializable("date");
        } else {
            mSearchDate = null;
        }

        getData();
    }

    protected void getData() {
        AsyncTask<Void, Integer, IrailDataResponse<RouteResult>> t = new AsyncTask<Void, Integer, IrailDataResponse<RouteResult>>() {

            @Override
            protected void onPostExecute(IrailDataResponse<RouteResult> response) {
                super.onPostExecute(response);

                vRefreshLayout.setRefreshing(false);

                if (response.isSuccess()) {
                    initialLoadCompleted = true;
                    mRoutes = response.getData();
                    showData(mRoutes.getRoutes());
                } else {
                    // only finish if we're loading new data
                    ErrorDialogFactory.showErrorDialog(response.getException(), RouteActivity.this, mRoutes == null);
                }

            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showData(null);

                setTitle(R.string.title_route);
                setSubTitle(mSearchFrom.getLocalizedName() + " - " + mSearchTo.getLocalizedName());

                if (mSearchDate != null) {
                    vWarningNotRealtime.setVisibility(View.VISIBLE);
                    @SuppressLint("SimpleDateFormat")
                    DateFormat df = new SimpleDateFormat(getString(R.string.warning_not_realtime_datetime));
                    vWarningNotRealtimeText.setText(String.format("%s %s", getString(R.string.warning_not_realtime), df.format(mSearchDate)));
                } else {
                    vWarningNotRealtime.setVisibility(View.GONE);
                }
            }

            @Override
            protected IrailDataResponse<RouteResult> doInBackground(Void... arglist) {

                IrailDataProvider api = IrailFactory.getDataProviderInstance();
                if (mSearchDate != null) {
                    return api.getRoute(mSearchFrom, mSearchTo, mSearchDate, mSearchTimeType);
                } else {
                    return api.getRoute(mSearchFrom, mSearchTo, new Date(), mSearchTimeType);
                }

            }
        };

        if (runningTask != null && runningTask.getStatus() != AsyncTask.Status.FINISHED) {
            runningTask.cancel(true);
        }

        t.execute();
        runningTask = t;
    }

    protected void getNextData() {
        if (!initialLoadCompleted) {
            return;
        }

        AsyncTask<Void, Integer, IrailDataResponse<Route[]>> t = new AsyncTask<Void, Integer, IrailDataResponse<Route[]>>() {

            @Override
            protected void onPostExecute(IrailDataResponse<Route[]> response) {
                super.onPostExecute(response);

                if (response.isSuccess()) {
                    // mRoutes is updated with new routes
                    showData(mRoutes.getRoutes());
                } else {
                    ErrorDialogFactory.showErrorDialog(response.getException(), RouteActivity.this, false);
                    ((RouteCardAdapter) vRecyclerView.getAdapter()).resetInfiniteScrollingState();
                }
            }

            @Override
            protected IrailDataResponse<Route[]> doInBackground(Void... arglist) {

                return RouteActivity.this.mRoutes.getNextResults();

            }
        };
        t.execute();
        runningTask = t;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (runningTask != null && runningTask.getStatus() != AsyncTask.Status.FINISHED) {
            runningTask.cancel(true);
        }
    }

    protected void showData(Route[] routeList) {
        if (mSearchDate == null) {
            vWarningNotRealtime.setVisibility(View.GONE);
        }

        if (routeList != null) {
            setTitle(R.string.title_route);
            // Ensure we show the correct from-to by showing it from the actual route result
            setSubTitle(routeList[0].getDepartureStation().getLocalizedName() + " - " + routeList[0].getArrivalStation().getLocalizedName());
        }

        ((RouteCardAdapter) vRecyclerView.getAdapter()).updateRoutes(routeList);
    }

    @Override
    protected int getLayout() {
        return R.layout.activity_route;
    }

    @Override
    protected int getMenuLayout() {
        return R.menu.actionbar_searchresult_routes;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_swap:
                Station h = this.mSearchTo;
                this.mSearchTo = this.mSearchFrom;
                this.mSearchFrom = h;
                this.setFavoriteDisplayState(this.isFavorite());
                // Empty the screen
                this.getData();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        return new RouteCardAdapter(this, vRecyclerView, this, null);
    }

    @Override
    public void onDateTimePicked(Date date) {
        mSearchDate = date;

        // empty the view while loading
        this.showData(null);

        // load the route list again for the new date
        getData();
    }

    @Override
    public void loadMoreRecyclerviewItems() {
        getNextData();
    }

    public void markFavorite(boolean favorite) {
        if (favorite) {
            persistentQueryProvider.addFavoriteRoute(mSearchFrom, mSearchTo);
            Snackbar.make(vLayoutRoot, R.string.marked_route_favorite, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            RouteActivity.this.markFavorite(false);
                        }
                    })
                    .show();
        } else {
            persistentQueryProvider.removeFavoriteRoute(mSearchFrom, mSearchTo);
            Snackbar.make(vLayoutRoot, R.string.unmarked_route_favorite, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            RouteActivity.this.markFavorite(true);
                        }
                    })
                    .show();
        }
        setFavoriteDisplayState(favorite);
    }

    public boolean isFavorite() {
        return persistentQueryProvider.isFavoriteRoute(mSearchFrom, mSearchTo);
    }
}
