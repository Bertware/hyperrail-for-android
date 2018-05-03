package be.bertmarcelis.thesis.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;
import be.bertmarcelis.thesis.irail.db.StationsDb;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.RouteResult;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendRoutesRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailRoutesRequest;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

@RunWith(AndroidJUnit4.class)
public class ConnectionsBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<RouteResult> {

    private volatile   HashMap<String, Long> start;
    private volatile   HashMap<String, Long> end;
    private volatile   ArrayList<String> done;
    private volatile boolean free = true;

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    @Test
    public void benchmark() throws StationNotResolvedException {

        final ArrayList<IrailRoutesRequest> requests = new ArrayList<>();

        ArrayList<String> querylog = new ArrayList<>();

        try (InputStream in = InstrumentationRegistry.getContext().getResources().openRawResource(be.bertmarcelis.thesis.test.R.raw.irailapi_connections)) {
            java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
            while (s.hasNextLine()) querylog.add(s.nextLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        IrailStationProvider stationProvider = new StationsDb(InstrumentationRegistry.getTargetContext());

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
        LinkedConnectionsApi api = new LinkedConnectionsApi(InstrumentationRegistry.getTargetContext());
        //api.setCacheEnabled(false);


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
            request.setCallback(ConnectionsBenchmark.this, ConnectionsBenchmark.this, request.toString());

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
        Log.d("BENCHMARK", "ready after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        done.add((String) tag);
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        free = true;
        Log.d("BENCHMARK", "ready after " + ms + "ms");
    }

}

