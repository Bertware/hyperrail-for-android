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

package be.hyperrail.android.irail.implementation;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import be.hyperrail.android.irail.contracts.IrailDataProvider;
import be.hyperrail.android.irail.contracts.IrailDataResponse;
import be.hyperrail.android.irail.contracts.IrailParser;
import be.hyperrail.android.irail.contracts.IrailStationProvider;
import be.hyperrail.android.irail.contracts.RouteTimeDefinition;
import be.hyperrail.android.irail.db.Station;
import be.hyperrail.android.irail.exception.InvalidResponseException;
import be.hyperrail.android.irail.exception.NetworkDisconnectedException;
import be.hyperrail.android.irail.exception.NotFoundException;

/**
 * Synchronous API for api.irail.be
 *
 * @inheritDoc
 */
public class IrailApi implements IrailDataProvider {

    private static final String LOGTAG = "iRailApi";

    public IrailApi(IrailParser parser, IrailStationProvider stationProvider) {
        this.parser = parser;
        this.stationProvider = stationProvider;
    }

    private final IrailParser parser;
    private final IrailStationProvider stationProvider;

    public IrailDataResponse<RouteResult> getRoute(String from, String to) {
        return getRoute(from, to, new Date());
    }

    public IrailDataResponse<RouteResult> getRoute(String from, String to, Date timeFilter) {
        return getRoute(from, to, timeFilter, RouteTimeDefinition.DEPART);
    }

    public IrailDataResponse<RouteResult> getRoute(String from, String to, Date timeFilter, RouteTimeDefinition timeFilterType) {
        return getRoute(stationProvider.getStationByName(from), stationProvider.getStationByName(to), timeFilter, timeFilterType);
    }

    public IrailDataResponse<RouteResult> getRoute(Station from, Station to) {
        return getRoute(from, to, new Date());
    }

    public IrailDataResponse<RouteResult> getRoute(Station from, Station to, Date timeFilter) {
        return getRoute(from, to, timeFilter, RouteTimeDefinition.DEPART);
    }

    public IrailDataResponse<RouteResult> getRoute(Station from, Station to, Date timeFilter, RouteTimeDefinition timeFilterType) {

        // https://api.irail.be/connections/?to=Halle&from=Brussels-south&date={dmy}&time=2359&timeSel=arrive or depart&format=json

        // suppress errors, this formatting is for an API call
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateformat = new SimpleDateFormat("ddMMyy");
        @SuppressLint("SimpleDateFormat") SimpleDateFormat timeformat = new SimpleDateFormat("HHmm");

        if (from == null || to == null) {
            return new ApiResponse<>(null, new NotFoundException("", "One or both stations are null"));
        }

        String from_name;
        String to_name;
        try {
            from_name = URLEncoder.encode(from.getName(), "UTF-8");
            to_name = URLEncoder.encode(to.getName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            from_name = from.getName();
            to_name = to.getName();
            e.printStackTrace();
        }

        String url = "https://api.irail.be/connections/?format=json"
                + "&to=" + to_name
                + "&from=" + from_name
                + "&date=" + dateformat.format(timeFilter)
                + "&time=" + timeformat.format(timeFilter);

        if (timeFilterType == RouteTimeDefinition.DEPART) {
            url += "&timeSel=depart";
        } else {
            url += "&timeSel=arrive";
        }

        try {
            JSONObject data = getJsonData(url);
            return new ApiResponse<>(parser.parseRouteResult(data, from, to, timeFilter, timeFilterType));
        } catch (NetworkDisconnectedException | InvalidResponseException e) {
            return new ApiResponse<>(null, e);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get routes: " + e.getMessage());
            return new ApiResponse<>(null, e);
        }
    }

    public IrailDataResponse<LiveBoard> getLiveboard(String name) {
        return getLiveboard(name, new Date());
    }

    public IrailDataResponse<LiveBoard> getLiveboard(String name, Date timeFilter) {
        return getLiveboard(name, timeFilter, RouteTimeDefinition.DEPART);
    }

    public IrailDataResponse<LiveBoard> getLiveboard(String name, Date timeFilter, RouteTimeDefinition timeFilterType) {
        // https://api.irail.be/liveboard/?station=Halle&fast=true

        // suppress errors, this formatting is for an API call
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateformat = new SimpleDateFormat("ddMMyy");
        @SuppressLint("SimpleDateFormat") SimpleDateFormat timeformat = new SimpleDateFormat("HHmm");

        try {
            name = URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String url = "https://api.irail.be/liveboard/?format=json"
                // TODO: use id here instead of name, supported by API but slow ATM
                + "&station=" + name
                + "&date=" + dateformat.format(timeFilter)
                + "&time=" + timeformat.format(timeFilter)
                + "&arrdep=" + ((timeFilterType == RouteTimeDefinition.DEPART) ? "dep" : "arr");

        try {
            return new ApiResponse<>(parser.parseLiveboard(getJsonData(url), timeFilter));
        } catch (NetworkDisconnectedException | InvalidResponseException e) {
            return new ApiResponse<>(null, e);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get liveboard: " + e.getMessage());
            return new ApiResponse<>(null, e);
        }
    }

    public IrailDataResponse<Train> getTrain(String id, Date day) {

        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateformat = new SimpleDateFormat("ddMMyy");

        String url = "https://api.irail.be/vehicle/?format=json"
                + "&id=" + id + "&date=" + dateformat.format(day);

        try {
            return new ApiResponse<>(parser.parseTrain(getJsonData(url), new Date()));
        } catch (NetworkDisconnectedException | InvalidResponseException e) {
            return new ApiResponse<>(null, e);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get train: " + e.getMessage());
            return new ApiResponse<>(null, e);
        }
    }

    public IrailDataResponse<Train> getTrain(String id) {
        return getTrain(id, new Date());
    }

    public IrailDataResponse<Disturbance[]> getDisturbances() {
        String url = "https://api.irail.be/disturbances/?format=json&lang=" + (Locale.getDefault().getISO3Language()).substring(0, 2);

        try {
            return new ApiResponse<>(parser.parseDisturbances(getJsonData(url)));
        } catch (NetworkDisconnectedException | InvalidResponseException e) {
            return new ApiResponse<>(null, e);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get disturbances: " + e.getMessage());
            return new ApiResponse<>(null, e);
        }
    }

    private static JSONObject getJsonData(String address) throws NetworkDisconnectedException, InvalidResponseException, FileNotFoundException {
        String data;

        try {
            data = getData(address);
        } catch (IOException e) {
            if (e instanceof FileNotFoundException) {
                throw (FileNotFoundException) e;
            } else {
                throw new NetworkDisconnectedException(address);
            }
        }

        if (data == null) {
            throw new NetworkDisconnectedException(address);
        }

        try {
            return new JSONObject(data);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to load json", e);
            throw new InvalidResponseException(address, data);
        }
    }

    /**
     * Get data from a URL as string
     *
     * @param address The full address, including protocol definition
     * @return The returned data in string format
     */
    private static String getData(String address) throws IOException {
        return getData(address, 0);
    }

    /**
     * Get data from a URL as string
     *
     * @param attempt The number of this try. Used for recursion, errors will cause up to 3 retries.
     * @param address The full address, including protocol definition
     * @return The returned data in string format
     */
    private static String getData(String address, int attempt) throws IOException {
        try {
            URL url = new URL(address);
            Log.d("IrailAPI", "Loading " + address);
            // Read all the text returned by the server
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String line, text = "";
            while ((line = in.readLine()) != null) {
                text += line;
            }
            in.close();
            return text;
        } catch (Exception e) {
            if (attempt < 3) {
                Log.w("IrailAPI", "Failed to load data, retrying... ", e);
                return getData(address, attempt + 1);
            } else {
                Log.e(LOGTAG, "Failed to load data, exited after multiple attemts... ", e);
                throw e;
            }
        }
    }

}
