package be.bertmarcelis.thesis.test;

import android.net.TrafficStats;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

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
import java.util.Map;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.RouteTimeDefinition;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;
import be.bertmarcelis.thesis.irail.db.StationsDb;
import be.bertmarcelis.thesis.irail.implementation.Lc2IrailApi;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;
import be.bertmarcelis.thesis.irail.implementation.Liveboard;
import be.bertmarcelis.thesis.irail.implementation.requests.ExtendLiveboardRequest;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailLiveboardRequest;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

@RunWith(AndroidJUnit4.class)
public class DeparturesArrivalsIncrementalResultsBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<Liveboard> {

    private volatile HashMap<String, Long> mTimeStart;
    private volatile HashMap<String, Long[]> mTimeEnd;
    private volatile HashMap<String, Long> mRxBytesStart;
    private volatile HashMap<String, Long[]> mRxBytesEnd;
    private volatile HashMap<String, Long> mTxBytesStart;
    private volatile HashMap<String, Long[]> mTxBytesEnd;
    private volatile ArrayList<String> done;
    private volatile boolean free = true;

    private static final int TARGET_RESULTS = 200;
    private IrailDataProvider api;
    private int attempts = 0;
    private static RequestQueue mRq;

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    @Test
    public void benchmark() throws StationNotResolvedException {

        final ArrayList<String> stations = new ArrayList<>();
        final ArrayList<DateTime> queryDates = new ArrayList<>();

        ArrayList<String> querylog = new ArrayList<>();
        mRq = Volley.newRequestQueue(InstrumentationRegistry.getContext());
        try (InputStream in = InstrumentationRegistry.getContext().getResources().openRawResource(be.bertmarcelis.thesis.test.R.raw.irailapi_liveboard)) {
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
        Log.w("BENCHMARK", "LC");
        api = new LinkedConnectionsApi(InstrumentationRegistry.getTargetContext());
        benchmark(stations, queryDates);
        Log.w("BENCHMARK", "LC2IRAIL");
        api = new Lc2IrailApi(InstrumentationRegistry.getTargetContext());
        benchmark(stations, queryDates);
    }

    public void benchmark(ArrayList<String> stations, ArrayList<DateTime> queryDates) throws StationNotResolvedException {
        done = new ArrayList<>();
        mTimeStart = new HashMap<>();
        mTimeEnd = new HashMap<>();
        mRxBytesStart = new HashMap<>();
        mRxBytesEnd = new HashMap<>();
        mTxBytesStart = new HashMap<>();
        mTxBytesEnd = new HashMap<>();

        IrailStationProvider stationProvider = new StationsDb(InstrumentationRegistry.getTargetContext());

        for (int i = 0; i < stations.size(); i += 20) {
            String station = stations.get(i);
            while (!free) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            attempts = 0;
            Log.d("BENCHMARK", i + "/" + stations.size());
            free = false;

            IrailLiveboardRequest r = new IrailLiveboardRequest(stationProvider.getStationByUri(station), RouteTimeDefinition.DEPART_AT, Liveboard.LiveboardType.DEPARTURES, queryDates.get(i));
            mTimeStart.put(r.toString(), DateTime.now().getMillis());
            mTxBytesStart.put(r.toString(), TrafficStats.getUidTxBytes(android.os.Process.myUid()));
            mRxBytesStart.put(r.toString(), TrafficStats.getUidRxBytes(android.os.Process.myUid()));
            r.setCallback(DeparturesArrivalsIncrementalResultsBenchmark.this, DeparturesArrivalsIncrementalResultsBenchmark.this, r.toString());

            api.getLiveboard(r);
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
                Long t1 = mTimeStart.get(request);
                Long[] times = mTimeEnd.get(request);
                StringBuilder subresult = new StringBuilder();
                subresult.append(t1.toString()).append(",");
                for (Long time : times) {
                    if (time - t1 >= 0 && time - t1 < 30000) {
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
                StringBuilder subresult = new StringBuilder();
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
                StringBuilder subresult = new StringBuilder();
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
        post("INCRTEST-" + apiName, result.toString());
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        Duration d = new Duration(mTimeStart.get(tag), DateTime.now().getMillis());
        long ms = d.getMillis();
        if (mTimeEnd.containsKey(tag) && (mTimeEnd.get(tag)[50] - mTimeStart.get(tag)) > 0) {
            done.add((String) tag);
        }
        free = true;
        Log.d("BENCHMARK", "error after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull Liveboard data, Object tag) {
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
        for (; i < data.getStops().length && i < TARGET_RESULTS; i++) {
            times[i] = millis;
            rx[i] = TrafficStats.getUidRxBytes(android.os.Process.myUid());
            tx[i] = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        }
        mTimeEnd.put((String) tag, times);
        mTxBytesEnd.put(key, tx);
        mRxBytesEnd.put(key, rx);

        Duration d = new Duration(mTimeStart.get(tag), millis);
        long ms = d.getMillis();
        attempts++;
        if ((i < 50 && ms < 60000) || (i < TARGET_RESULTS && ms < 30000) && attempts < 32) {
            //Log.d("BENCHMARK", "extend after " + ms + "ms (" + i + " results)");
            ExtendLiveboardRequest request = new ExtendLiveboardRequest(data, ExtendLiveboardRequest.Action.APPEND);
            request.setCallback(this, this, tag);
            api.extendLiveboard(request);
        } else {
            free = true;
            Log.d("BENCHMARK", "ready after " + ms + "ms");
            done.add((String) tag);
        }

    }

    private static void post(final String name, final String data) {
        String url = "https://log.thesis.bertmarcelis.be/post.php";
        final String TAG = "POSTDATA";
        //String uri = String.format(Locale.US, URL);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                                                        new com.android.volley.Response.Listener<String>() {
                                                            @Override
                                                            public void onResponse(String response) {
                                                                Log.d(TAG, "String Success :" + response);
                                                            }
                                                        },
                                                        new com.android.volley.Response.ErrorListener() {
                                                            @Override
                                                            public void onErrorResponse(VolleyError error) {
                                                                Log.d(TAG, "String  Error In Request :" + error.toString());
                                                            }
                                                        }) {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                return super.parseNetworkResponse(response);
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return data.getBytes();
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                String AuthToken = "903825";
                headers.put("auth", AuthToken);
                headers.put("source", name);
                return headers;
            }
        };
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                                            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mRq.add(stringRequest);
    }
}

