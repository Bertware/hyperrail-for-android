package be.bertmarcelis.thesis.batterytesting;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import be.bertmarcelis.thesis.R;
import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;
import be.bertmarcelis.thesis.irail.db.StationsDb;
import be.bertmarcelis.thesis.irail.factories.IrailFactory;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

public class DeparturesArrivalsBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<Liveboard> {

    private volatile HashMap<String, Long> start;
    private volatile HashMap<String, Long> end;
    private volatile ArrayList<String> done;
    private volatile boolean free = true;
    IrailDataProvider api = IrailFactory.getDataProviderInstance();
    private int attempts = 0;
    private  final static int TARGET_RESULTS = 10;

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    public void benchmark(Context context) throws StationNotResolvedException {

        final ArrayList<String> stations = new ArrayList<>();
        final ArrayList<DateTime> queryDates = new ArrayList<>();

        ArrayList<String> querylog = new ArrayList<>();

        try (InputStream in = context.getResources().openRawResource(R.raw.irailapi_liveboard)) {
            java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
            while (s.hasNextLine()) querylog.add(s.nextLine());
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String s : querylog) {
            JSONObject json = null;
            try {
                json = new JSONObject(s);
                if (!json.has("query")) continue;
                stations.add(json.getJSONObject("query").getJSONObject("departureStop").getString("@id"));
                queryDates.add(DateTime.parse(json.getJSONObject("query").getString("dateTime")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        done = new ArrayList<>();

        start = new HashMap<>();
        end = new HashMap<>();

        //api.setCacheEnabled(false);

        IrailStationProvider stationProvider = IrailFactory.getStationsProviderInstance();

        for (int i = 0; i < stations.size(); i += 20) {
            String station = stations.get(i);
            while (!free) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (i > 0 && i % 100 == 0) {
                long min = 5000, max = 0, avg = 0;
                for (String t : done) {
                    Duration d = new Duration(start.get(t), end.get(t));
                    long ms = d.getMillis();
                    if (ms < min) {
                        min = ms;
                    }
                    if (ms > max) {
                        max = ms;
                    }
                    avg += ms;
                }
                avg = avg / done.size();
                Log.e("BENCHMARK", "min " + min + " avg " + avg + " max " + max);
            }

            Log.d("BENCHMARK", i + "/" + stations.size());
            free = false;

            attempts = 0;
            IrailLiveboardRequest r = new IrailLiveboardRequest(stationProvider.getStationByUri(station), RouteTimeDefinition.DEPART_AT, Liveboard.LiveboardType.DEPARTURES, queryDates.get(i));
            start.put(r.toString(), DateTime.now().getMillis());
            r.setCallback(this, this, r.toString());

            api.getLiveboard(r);
        }

        long min = 5000, max = 0, avg = 0;
        for (String station : done) {
            Duration d = new Duration(start.get(station), end.get(station));
            long ms = d.getMillis();
            if (ms < min) {
                min = ms;
            }
            if (ms > max) {
                max = ms;
            }
            avg += ms;
        }
        avg = avg / done.size();
        Log.e("BENCHMARK", "min " + min + " avg " + avg + " max " + max);
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        free = true;
        Log.d("BENCHMARK", "ready after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        done.add((String) tag);
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        attempts++;
        if (data.getStops().length < TARGET_RESULTS && attempts < 32) {
            Log.d("BENCHMARK", "extend after " + ms + "ms (" + data.getStops().length + " results)");
            ExtendLiveboardRequest request = new ExtendLiveboardRequest(data, ExtendLiveboardRequest.Action.APPEND);
            request.setCallback(this, this, tag);
            api.extendLiveboard(request);
        } else {
            free = true;
            Log.d("BENCHMARK", "ready after " + ms + "ms");
            done.add((String) tag);
        }
    }

}

