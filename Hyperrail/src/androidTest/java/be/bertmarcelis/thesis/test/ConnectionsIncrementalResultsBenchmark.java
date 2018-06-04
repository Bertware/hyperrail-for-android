package be.bertmarcelis.thesis.test;

import android.net.TrafficStats;
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
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;
import be.bertmarcelis.thesis.irail.db.StationsDb;
import be.bertmarcelis.thesis.irail.implementation.Lc2IrailApi;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;
import be.bertmarcelis.thesis.irail.implementation.RouteResult;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendRoutesRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailRoutesRequest;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

@RunWith(AndroidJUnit4.class)
public class ConnectionsIncrementalResultsBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<RouteResult> {

    private volatile HashMap<String, Long> start;
    private volatile HashMap<String, Long[]> mTimeEnd;
    private volatile HashMap<String, Long> mRxBytesStart;
    private volatile HashMap<String, Long[]> mRxBytesEnd;
    private volatile HashMap<String, Long> mTxBytesStart;
    private volatile HashMap<String, Long[]> mTxBytesEnd;
    private volatile ArrayList<String> done;
    private volatile boolean free = true;

    private static final int TARGET_RESULTS = 10;
    private IrailDataProvider api;
    private int attempts = 0;

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    @Test
    public void benchmark() throws StationNotResolvedException {
        final ArrayList<IrailRoutesRequest> requests = new ArrayList<>();

        ArrayList<String> querylog = new ArrayList<>();
        Log.w("BENCHMARK", "Loading queries");
        try (InputStream in = InstrumentationRegistry.getContext().getResources().openRawResource(R.raw.irailapi_connections)) {
            java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
            while (s.hasNextLine()) querylog.add(s.nextLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        IrailStationProvider stationProvider = new StationsDb(InstrumentationRegistry.getTargetContext());

        Log.w("BENCHMARK", "Parsing queries");
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

        Log.w("BENCHMARK", "LC");
        api = new LinkedConnectionsApi(InstrumentationRegistry.getTargetContext());
        free = true;
        benchmark(requests);
        Log.w("BENCHMARK", "LC2IRAIL");
        api = new Lc2IrailApi(InstrumentationRegistry.getTargetContext());
        free = true;
        benchmark(requests);
    }

    public void benchmark(ArrayList<IrailRoutesRequest> requests) throws StationNotResolvedException {
        done = new ArrayList<>();
        start = new HashMap<>();
        mTimeEnd = new HashMap<>();
        mRxBytesStart = new HashMap<>();
        mRxBytesEnd = new HashMap<>();
        mTxBytesStart = new HashMap<>();
        mTxBytesEnd = new HashMap<>();

        for (int i = 0; i < requests.size(); i += 15) {
            IrailRoutesRequest request = requests.get(i);
            while (!free) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            attempts = 0;
            Log.d("BENCHMARK", i + "/" + requests.size());
            free = false;

            start.put(request.toString(), DateTime.now().getMillis());
            mTxBytesStart.put(request.toString(), TrafficStats.getUidTxBytes(android.os.Process.myUid()));
            mRxBytesStart.put(request.toString(), TrafficStats.getUidRxBytes(android.os.Process.myUid()));
            request.setCallback(ConnectionsIncrementalResultsBenchmark.this, ConnectionsIncrementalResultsBenchmark.this, request.toString());

            api.getRoutes(request);
        }
        while (!free) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        StringBuilder result = new StringBuilder("TIJD");

        Log.e("BENCHMARK", "TIJD");
        for (String request : done) {
            try {
                Long t1 = start.get(request);
                Long[] times = mTimeEnd.get(request);
                StringBuilder subresult = new StringBuilder(request).append(",");
                subresult.append(t1.toString()).append(",");
                for (Long time : times) {
                    if (time - t1 >= 0 && time - t1 < 600000) {
                        subresult.append(time - t1);
                    }
                    subresult.append(",");
                }
                Log.e("BENCHMARK", subresult.toString());
                result.append(subresult).append("\n");
            } catch (Exception e) {
                Log.e("DepArrBench", e.getMessage());
            }
        }
        result.append("TX TX TX").append("\n");
        Log.e("BENCHMARK", "TX");
        for (String request : done) {
            try {
                Long startBytes = mTxBytesStart.get(request);
                Long[] bytesArray = mTxBytesEnd.get(request);
                StringBuilder subresult = new StringBuilder(request).append(",");
                subresult.append(startBytes.toString()).append(",");
                for (Long bytes : bytesArray) {
                    if (bytes - startBytes >= 0 && bytes >= 0) {
                        subresult.append(bytes - startBytes);
                    }
                    subresult.append(",");
                }
                Log.e("BENCHMARK", subresult.toString());
                result.append(subresult).append("\n");
            } catch (Exception e) {
                Log.e("DepArrBench", e.getMessage());
            }
        }
        Log.e("BENCHMARK", "RX");
        result.append("RX RX RX").append("\n");
        for (String request : done) {
            try {
                Long startBytes = mRxBytesStart.get(request);
                Long[] bytesArrat = mRxBytesEnd.get(request);
                StringBuilder subresult = new StringBuilder(request).append(",");
                subresult.append(startBytes.toString()).append(",");
                for (Long bytes : bytesArrat) {
                    if (bytes - startBytes >= 0 && bytes >= 0) {
                        subresult.append(bytes - startBytes);
                    }
                    subresult.append(",");
                }
                Log.e("BENCHMARK", subresult.toString());
                result.append(subresult).append("\n");
            } catch (Exception e) {
                Log.e("DepArrBench", e.getMessage());
            }
        }

        String apiName = "LC";
        if (api instanceof Lc2IrailApi) {
            apiName = "LC2Irail";
        }
        Log.d("INCRTEST-" + apiName, result.toString());
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        Duration d = new Duration(start.get(tag), DateTime.now().getMillis());
        long ms = d.getMillis();
        if (mTimeEnd.containsKey(tag) && mTimeEnd.get(tag)[TARGET_RESULTS / 2 - 1] > 0) {
            done.add((String) tag);
        }
        free = true;

        Log.d("BENCHMARK", "error after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull RouteResult data, Object tag) {
        Long millis = DateTime.now().getMillis();
        String key = (String) tag;
        if (!mTimeEnd.containsKey(key)) {
            Long[] initial = new Long[TARGET_RESULTS];
            Long[] initialrx = new Long[TARGET_RESULTS];
            Long[] initialtx = new Long[TARGET_RESULTS];
            for (int i = 0; i < TARGET_RESULTS; i++) {
                initial[i] = 0L;
                initialrx[i] = -1L;
                initialtx[i] = -1L;
            }
            mTimeEnd.put(key, initial);
            mTxBytesEnd.put(key, initialtx);
            mRxBytesEnd.put(key, initialrx);
        }
        Long[] times = mTimeEnd.get(key);
        Long[] rx = mRxBytesEnd.get(key);
        Long[] tx = mTxBytesEnd.get(key);
        int i = 0;
        while (i < TARGET_RESULTS && times[i] > 0) {
            i++;
        }
        for (; i < data.getRoutes().length && i < TARGET_RESULTS; i++) {
            times[i] = millis;
            rx[i] = TrafficStats.getUidRxBytes(android.os.Process.myUid());
            tx[i] = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        }
        mTimeEnd.put((String) tag, times);
        mTxBytesEnd.put((String) tag, tx);
        mRxBytesEnd.put((String) tag, rx);

        Duration d = new Duration(start.get(tag), millis);
        long ms = d.getMillis();
        attempts++;
        if (i < TARGET_RESULTS && attempts < 16) {
            Log.d("BENCHMARK", "extend after " + ms + "ms (" + i + " results)");
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

