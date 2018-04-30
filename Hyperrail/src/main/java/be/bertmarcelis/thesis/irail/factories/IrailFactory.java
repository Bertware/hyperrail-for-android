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

package be.bertmarcelis.thesis.irail.factories;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.perf.metrics.AddTrace;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.contracts.IrailStationProvider;
import be.bertmarcelis.thesis.irail.contracts.MeteredApi;
import be.bertmarcelis.thesis.irail.db.StationsDb;
import be.bertmarcelis.thesis.irail.implementation.IrailApi;
import be.bertmarcelis.thesis.irail.implementation.Lc2IrailApi;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

/**
 * Factory to provide singleton data providers (for stations and API calls) and a singleton parser.
 * This factory should be setup at application start. Once setup, context isn't required for calls which return an instance.
 */
public class IrailFactory {

    private static IrailStationProvider stationProviderInstance;
    private static IrailDataProvider dataProviderInstance;
    private static RequestQueue mRq;
    private static String lastCreated = "";

    @AddTrace(name = "IrailFactory.setup")
    public static void setup(Context applicationContext) {
        String api = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("api", "lc2irail");

        if (lastCreated.equals(api)){
            return;
        }

        if (!lastCreated.isEmpty()){
            logMeteredApiData(applicationContext);
        }

        lastCreated = api;
        stationProviderInstance = new StationsDb(applicationContext);

        switch (api) {
            case "irail":
                dataProviderInstance = new IrailApi(applicationContext);
                break;
            case "lc2irail":
                dataProviderInstance = new Lc2IrailApi(applicationContext);
                break;
            case "lc":
                dataProviderInstance = new LinkedConnectionsApi(applicationContext);
                break;
        }
        FirebaseCrash.logcat(INFO.intValue(), "IrailFactory", "Set-up completed with API " + api);
        Toast.makeText(applicationContext, "Loaded API: " + api, Toast.LENGTH_LONG).show();
    }

    public static IrailStationProvider getStationsProviderInstance() {
        if (stationProviderInstance == null) {
            FirebaseCrash.logcat(SEVERE.intValue(), "Irail16Factory", "Failed to provide station provider! Call setup() before calling any factory method!");
            FirebaseCrash.report(new Exception("IrailStationProvider was requested before the factory was initialized"));
            throw new IllegalStateException();
        }
        return stationProviderInstance;
    }

    public static IrailDataProvider getDataProviderInstance() {
        if (dataProviderInstance == null) {
            FirebaseCrash.logcat(SEVERE.intValue(), "Irail16Factory", "Failed to provide data provider! Call setup() before calling any factory method!");
            FirebaseCrash.report(new Exception("IrailDataProvider was requested before the factory was initialized"));
            throw new IllegalStateException();
        }
        return dataProviderInstance;
    }

    private static void logMeteredApiData(Context context) {
        if (dataProviderInstance == null) {
            return;
        }
        IrailDataProvider provider = getDataProviderInstance();
        if (!(provider instanceof MeteredApi)) {
            return;
        }

        final StringBuilder body = new StringBuilder();
        MeteredApi.MeteredRequest[] requests = ((MeteredApi) provider).getMeteredRequests();
        for (MeteredApi.MeteredRequest request : requests) {
            body.append(request.toString()).append("\n");
        }

        Log.d("IrailFactory", body.toString());
        if (mRq == null) {
            mRq = Volley.newRequestQueue(context);
        }
        String name = "unknown";
        if (dataProviderInstance instanceof Lc2IrailApi){
            name = "lc2irail";
        }
        if (dataProviderInstance instanceof  LinkedConnectionsApi){
            name = "lc";
        }
        final String finalName = name;
        new AlertDialog.Builder(context).setMessage("Testresultaten opslaan?").setPositiveButton("Ja", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                post(finalName, body.toString());
            }
        }).setNegativeButton("Nee", null).show();
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
