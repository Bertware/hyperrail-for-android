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
import be.bertmarcelis.thesis.irail.implementation.Lc2IrailApi;
import be.bertmarcelis.thesis.irail.implementation.RouteResult;
import be.bertmarcelis.thesis.irail.implementation.linkedconnections.LinkedConnectionsProvider;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendRoutesRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailRoutesRequest;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */


public class ConnectionsBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<RouteResult> {

    private static final int TARGET_RESULTS = 10;
    private volatile HashMap<String, Long> start;
    private volatile HashMap<String, Long> end;
    private volatile ArrayList<String> done;
    private volatile boolean free = true;
    private int attempts = 0;
    IrailStationProvider stationProvider = IrailFactory.getStationsProviderInstance();
    IrailDataProvider api = IrailFactory.getDataProviderInstance();

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    public void benchmark(Context context) throws StationNotResolvedException {

        final ArrayList<IrailRoutesRequest> requests = new ArrayList<>();

        ArrayList<String> querylog = new ArrayList<>();

        try (InputStream in = context.getResources().openRawResource(R.raw.irailapi_connections)) {
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
                IrailRoutesRequest request = new IrailRoutesRequest(stationProvider.getStationByUri(json.getJSONObject("query").getJSONObject("departureStop").getString("@id")),
                                                                    stationProvider.getStationByUri(json.getJSONObject("query").getJSONObject("arrivalStop").getString("@id")),
                                                                    RouteTimeDefinition.DEPART_AT,
                                                                    DateTime.parse(json.getJSONObject("query").getString("dateTime")));
                requests.add(request);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        done = new ArrayList<>();
        start = new HashMap<>();
        end = new HashMap<>();

        for (int i = 0; i < requests.size(); i += 20) {
            IrailRoutesRequest request = requests.get(i);
            while (!free) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.d("BENCHMARK", i + "/" + requests.size());
            free = false;

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

            start.put(request.toString(), DateTime.now().getMillis());
            request.setCallback(this, this, request.toString());
            attempts = 0;
            api.getRoutes(request);
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
        Log.d("BENCHMARK", "erored after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        done.add((String) tag);
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        attempts++;
        if (data.getRoutes().length < TARGET_RESULTS && ms < 20000 && attempts < 64) {
            Log.d("BENCHMARK", "extend after " + ms + "ms (" + data.getRoutes().length + " results)");
            ExtendRoutesRequest request = new ExtendRoutesRequest(data, ExtendRoutesRequest.Action.APPEND);
            request.setCallback(this, this, tag);
            api.extendRoutes(request);
        } else {
            free = true;
            Log.d("BENCHMARK", "ready after " + ms + "ms");
            done.add((String) tag);
        }
    }
}

