/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.android.irail.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.preference.PreferenceManager;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.perf.metrics.AddTrace;

import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Scanner;

import be.hyperrail.android.R;
import be.hyperrail.android.irail.contracts.IrailStationProvider;

import static be.hyperrail.android.irail.db.StationsDataContract.SQL_CREATE_INDEX_ID;
import static be.hyperrail.android.irail.db.StationsDataContract.SQL_CREATE_INDEX_NAME;
import static be.hyperrail.android.irail.db.StationsDataContract.SQL_CREATE_TABLE_FACILITIES;
import static be.hyperrail.android.irail.db.StationsDataContract.SQL_CREATE_TABLE_STATIONS;
import static be.hyperrail.android.irail.db.StationsDataContract.SQL_DELETE_TABLE_FACILITIES;
import static be.hyperrail.android.irail.db.StationsDataContract.SQL_DELETE_TABLE_STATIONS;
import static be.hyperrail.android.irail.db.StationsDataContract.StationFacilityColumns;
import static be.hyperrail.android.irail.db.StationsDataContract.StationsDataColumns;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
 * Database for querying stations
 */
public class StationsDb extends SQLiteOpenHelper implements IrailStationProvider {

    // If you change the database schema, you must increment the database version.
    // year/month/day/increment
    private static final int DATABASE_VERSION = 18012500;

    // Name of the database file
    private static final String DATABASE_NAME = "stations.db";

    // Logtag for logging purpose
    private static final String LOGTAG = "database";

    private final Context context;

    public StationsDb(Context applicationContext) {
        super(applicationContext, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = applicationContext;
    }

    /**
     * Create the database.
     *
     * @param db Handle in which the database should be created.
     */
    public void onCreate(SQLiteDatabase db) {
        FirebaseCrash.logcat(INFO.intValue(), LOGTAG, "Creating stations database");

        db.execSQL(SQL_CREATE_TABLE_STATIONS);
        db.execSQL(SQL_CREATE_TABLE_FACILITIES);
        db.execSQL(SQL_CREATE_INDEX_ID);
        db.execSQL(SQL_CREATE_INDEX_NAME);

        FirebaseCrash.logcat(INFO.intValue(), LOGTAG, "Filling stations database");
        fill(db);
        FirebaseCrash.logcat(INFO.intValue(), LOGTAG, "Stations database ready");
    }

    /**
     * Fill the database with data from the embedded CSV file (raw resource).
     *
     * @param db The database to fill
     */
    @AddTrace(name = "fillStationsDb")
    private void fill(SQLiteDatabase db) {

        try (Scanner lines = new Scanner(context.getResources().openRawResource(R.raw.stations))) {
            lines.useDelimiter("\n");

            while (lines.hasNext()) {
                String line = lines.next();

                try (Scanner fields = new Scanner(line)) {
                    fields.useDelimiter(",");

                    ContentValues values = new ContentValues();

                    String id = fields.next();

                    // By default, the CSV contains ids in iRail URI format,
                    // reformat them to BE.NMBS.XXXXXXXX (which is also iRail compliant)
                    if (id.startsWith("http")) {
                        id = id.replace("http://irail.be/stations/NMBS/", "BE.NMBS.");
                    }

                    // Store ID as BE.NMBS.XXXXXXXX
                    values.put(StationsDataColumns._ID, id);
                    // Replace special characters (for search purposes)
                    values.put(StationsDataContract.StationsDataColumns.COLUMN_NAME_NAME, cleanAccents(fields.next()));
                    values.put(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR, cleanAccents(fields.next()));
                    values.put(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL, cleanAccents(fields.next()));
                    values.put(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE, cleanAccents(fields.next()));
                    values.put(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN, cleanAccents(fields.next()));
                    values.put(StationsDataColumns.COLUMN_NAME_COUNTRY_CODE, fields.next());

                    String field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationsDataColumns.COLUMN_NAME_LONGITUDE, Double.parseDouble(field));
                    } else {
                        values.put(StationsDataColumns.COLUMN_NAME_LONGITUDE, 0);
                    }

                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationsDataColumns.COLUMN_NAME_LATITUDE, Double.parseDouble(field));
                    } else {
                        values.put(StationsDataColumns.COLUMN_NAME_LATITUDE, 0);
                    }

                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        try {
                            values.put(StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES, Double.parseDouble(field));
                        } catch (NumberFormatException e) {
                            values.put(StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES, 0);
                        }
                    } else {
                        values.put(StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES, 0);
                    }

                    // Insert row
                    db.insert(StationsDataColumns.TABLE_NAME, null, values);
                }
            }
        }

        try (Scanner lines = new Scanner(context.getResources().openRawResource(R.raw.stationfacilities))) {
            lines.useDelimiter("\n");

            while (lines.hasNext()) {
                String line = lines.next();

                try (Scanner fields = new Scanner(line)) {
                    fields.useDelimiter(",");

                    ContentValues values = new ContentValues();

                    String id = fields.next();

                    // By default, the CSV contains ids in iRail URI format,
                    // reformat them to BE.NMBS.XXXXXXXX (which is also iRail compliant)
                    if (id.startsWith("http")) {
                        id = id.replace("http://irail.be/stations/NMBS/", "BE.NMBS.");
                    }

                    // Store ID as BE.NMBS.XXXXXXXX
                    values.put(StationFacilityColumns._ID, id);
                    // Skip name
                    fields.next();
                    values.put(StationFacilityColumns.COLUMN_STREET, fields.next());
                    values.put(StationFacilityColumns.COLUMN_ZIP, fields.next());
                    values.put(StationFacilityColumns.COLUMN_CITY, fields.next());
                    values.put(StationFacilityColumns.COLUMN_TICKET_VENDING_MACHINE, fields.next());
                    values.put(StationFacilityColumns.COLUMN_LUGGAGE_LOCKERS, fields.next());
                    values.put(StationFacilityColumns.COLUMN_FREE_PARKING, fields.next());
                    values.put(StationFacilityColumns.COLUMN_TAXI, fields.next());
                    values.put(StationFacilityColumns.COLUMN_BICYCLE_SPOTS, fields.next());
                    values.put(StationFacilityColumns.COLUMN_BLUE_BIKE, fields.next());
                    values.put(StationFacilityColumns.COLUMN_BUS, fields.next());
                    values.put(StationFacilityColumns.COLUMN_TRAM, fields.next());
                    values.put(StationFacilityColumns.COLUMN_METRO, fields.next());
                    values.put(StationFacilityColumns.COLUMN_WHEELCHAIR_AVAILABLE, fields.next());
                    values.put(StationFacilityColumns.COLUMN_RAMP, fields.next());
                    values.put(StationFacilityColumns.COLUMN_DISABLED_PARKING_SPOTS, fields.next());
                    values.put(StationFacilityColumns.COLUMN_ELEVATED_PLATFORM, fields.next());
                    values.put(StationFacilityColumns.COLUMN_ESCALATOR_UP, fields.next());
                    values.put(StationFacilityColumns.COLUMN_ESCALATOR_DOWN, fields.next());
                    values.put(StationFacilityColumns.COLUMN_ELEVATOR_PLATFORM, fields.next());
                    values.put(StationFacilityColumns.COLUMN_HEARING_AID_SIGNAL, fields.next());
                    String field = fields.next();

                    // If an opening time exists, a closing one also exists.
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_MONDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_MONDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_TUESDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_TUESDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_WEDNESDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_WEDNESDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_THURSDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_THURSDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_FRIDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_FRIDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_SATURDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_SATURDAY, fields.next());
                    } else {
                        fields.next();
                    }
                    field = fields.next();
                    if (field != null && !field.isEmpty()) {
                        values.put(StationFacilityColumns.COLUMN_SALES_OPEN_SUNDAY, field);
                        values.put(StationFacilityColumns.COLUMN_SALES_CLOSE_SUNDAY, fields.next());
                    } else {
                        fields.next();
                    }

                    // Insert row
                    db.insert(StationFacilityColumns.TABLE_NAME, null, values);
                }
            }
        }
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache from a local file, so just recreate it
        db.execSQL(SQL_DELETE_TABLE_STATIONS);
        db.execSQL(SQL_DELETE_TABLE_FACILITIES);
        onCreate(db);
    }

    private String cleanAccents(String s) {
        return s.replaceAll("[éÉèÈêÊëË]", "e")
                .replaceAll("[âÂåäÄ]", "a")
                .replaceAll("[öÖø]", "o")
                .replaceAll("[üÜ]", "u");
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache from a local file, so just recreate it
        onUpgrade(db, oldVersion, newVersion);
    }


    Station[] stationsOrderedBySizeCache;

    @Override
    @AddTrace(name="StationsDb.getStationsOrderBySize")
    public Station[] getStationsOrderBySize() {

        if (stationsOrderedBySizeCache != null){
            return stationsOrderedBySizeCache;
        }

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES
                },
                null,
                null,
                null,
                null,
                StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES + " DESC"
        );

        Station[] stations = loadStationCursor(c);
        c.close();
        db.close();

        stationsOrderedBySizeCache = stations;

        return stations;
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationsByNameOrderBySize")
    public Station[] getStationsByNameOrderBySize(String name) {
        name = cleanAccents(name);
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES
                },
                StationsDataColumns.COLUMN_NAME_NAME + " LIKE ?",
                new String[]{"%" + name + "%"},
                null,
                null,
                StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES + " DESC"
        );

        Station[] stations = loadStationCursor(c);
        c.close();
        db.close();
        return stations;
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationsOrderByLocation")
    public Station[] getStationsOrderByLocation(Location location) {
        return this.getStationsByNameOrderByLocation("", location);
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationsByNameOrderByLocation")
    public Station[] getStationsByNameOrderByLocation(String name, Location location) {
        SQLiteDatabase db = this.getReadableDatabase();

        double longitude = Math.round(location.getLongitude() * 1000000.0) / 1000000.0;
        double latitude = Math.round(location.getLatitude() * 1000000.0) / 1000000.0;
        name = cleanAccents(name);
        name = name.replaceAll("\\(\\w\\)", "");
        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES,
                        "(" + StationsDataColumns.COLUMN_NAME_LATITUDE + " - " + latitude + ")*(" + StationsDataColumns.COLUMN_NAME_LATITUDE + " - " + latitude + ")+("
                                + StationsDataColumns.COLUMN_NAME_LONGITUDE + " - " + longitude + ")*(" + StationsDataColumns.COLUMN_NAME_LONGITUDE + " - " + longitude
                                + ") AS distance"
                },
                StationsDataColumns.COLUMN_NAME_NAME + " LIKE ?",
                new String[]{"%" + name + "%"},
                null,
                null,
                "distance ASC, " + StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES + " DESC"
        );

        Station[] stations = loadStationCursor(c);
        c.close();
        db.close();
        return stations;
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationsOrderByLocationAndSize")
    public Station[] getStationsOrderByLocationAndSize(Location location, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        double longitude = Math.round(location.getLongitude() * 1000000.0) / 1000000.0;
        double latitude = Math.round(location.getLatitude() * 1000000.0) / 1000000.0;

        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES,
                        "(" + StationsDataColumns.COLUMN_NAME_LATITUDE + " - " + latitude + ")*(" + StationsDataColumns.COLUMN_NAME_LATITUDE + " - " + latitude
                                + ")+(" + StationsDataColumns.COLUMN_NAME_LONGITUDE + " - " + longitude + ")*(" + StationsDataColumns.COLUMN_NAME_LONGITUDE + " - " + longitude
                                + ") AS distance"
                },
                null,
                null,
                null,
                null,
                "distance ASC, " + StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES + " DESC",
                String.valueOf(limit)
        );

        Station[] stations = loadStationCursor(c);

        c.close();
        db.close();

        if (stations == null) {
            return null;
        }

        Arrays.sort(stations, new Comparator<Station>() {
            @Override
            public int compare(Station o1, Station o2) {
                return Float.compare(o2.getAvgStopTimes(), o1.getAvgStopTimes());
            }
        });

        return stations;
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationNames")
    public String[] getStationNames(Station[] Stations) {

        if (Stations == null || Stations.length == 0) {
            FirebaseCrash.logcat(WARNING.intValue(), LOGTAG, "Tried to load station names on empty station list!");
            return new String[0];
        }

        String[] results = new String[Stations.length];
        for (int i = 0; i < Stations.length; i++) {
            results[i] = Stations[i].getLocalizedName();
        }
        return results;
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationById")
    public Station getStationById(String id) {

        SQLiteOpenHelper StationsDbHelper = new StationsDb(context);
        SQLiteDatabase db = StationsDbHelper.getReadableDatabase();
        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES
                },
                StationsDataColumns._ID + "=?",
                new String[]{id},
                null,
                null,
                null,
                "1");

        Station[] results = loadStationCursor(c);

        c.close();
        db.close();

        if (results == null) {
            return null;
        }

        return results[0];
    }

    /**
     * @inheritDoc
     */
    @Override
    @AddTrace(name="StationsDb.getStationByName")
    public Station getStationByName(String name) {
        SQLiteOpenHelper StationsDbHelper = new StationsDb(context);
        SQLiteDatabase db = StationsDbHelper.getReadableDatabase();
        name = cleanAccents(name);
        name = name.replaceAll("\\(\\w\\)", "");
        String wcName = name.replaceAll("[^A-Za-z]", "%");
        Cursor c = db.query(
                StationsDataColumns.TABLE_NAME,
                new String[]{
                        StationsDataColumns._ID,
                        StationsDataColumns.COLUMN_NAME_NAME,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE,
                        StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN,
                        StationsDataColumns.COLUMN_NAME_COUNTRY_CODE,
                        StationsDataColumns.COLUMN_NAME_LATITUDE,
                        StationsDataColumns.COLUMN_NAME_LONGITUDE,
                        StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES
                },
                StationsDataColumns.COLUMN_NAME_NAME + " LIKE ? OR " + StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR + " LIKE ? OR " + StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL +
                        " LIKE ? OR " + StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE + " LIKE ? OR " + StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN + " LIKE ?",
                new String[]{wcName, wcName, wcName, wcName, wcName},
                null,
                null,
                StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES + " DESC",
                "1");
        if (c.getCount() < 1) {

            c.close();
            db.close();

            if (name.contains("/")) {
                String newname = name.substring(0, name.indexOf("/") - 1);
                FirebaseCrash.logcat(WARNING.intValue(), "SQLiteStationProvider", "Station not found: " + name + ", replacement search " + newname);
                return getStationByName(newname);
            } else if (name.contains("(")) {
                String newname = name.substring(0, name.indexOf("(") - 1);
                FirebaseCrash.logcat(WARNING.intValue(), "SQLiteStationProvider", "Station not found: " + name + ", replacement search " + newname);
                return getStationByName(newname);
            } else {
                FirebaseCrash.logcat(SEVERE.intValue(), "SQLiteStationProvider", "Station not found: " + name + ", cleaned search " + wcName);
                return null;
            }
        }

        Station[] results = loadStationCursor(c);

        c.close();
        db.close();

        if (results == null) {
            return null;
        }

        return results[0];
    }

    public StationFacilities getStationFacilitiesById(String id) {

        SQLiteOpenHelper StationsDbHelper = new StationsDb(context);
        SQLiteDatabase db = StationsDbHelper.getReadableDatabase();
        Cursor c = db.query(
                StationFacilityColumns.TABLE_NAME,
                new String[]{
                        StationFacilityColumns._ID,
                        StationFacilityColumns.COLUMN_STREET,
                        StationFacilityColumns.COLUMN_ZIP,
                        StationFacilityColumns.COLUMN_CITY,
                        StationFacilityColumns.COLUMN_TICKET_VENDING_MACHINE,
                        StationFacilityColumns.COLUMN_LUGGAGE_LOCKERS,
                        StationFacilityColumns.COLUMN_FREE_PARKING,
                        StationFacilityColumns.COLUMN_TAXI,
                        StationFacilityColumns.COLUMN_BICYCLE_SPOTS,
                        StationFacilityColumns.COLUMN_BLUE_BIKE,
                        StationFacilityColumns.COLUMN_BUS,
                        StationFacilityColumns.COLUMN_TRAM,
                        StationFacilityColumns.COLUMN_METRO,
                        StationFacilityColumns.COLUMN_WHEELCHAIR_AVAILABLE,
                        StationFacilityColumns.COLUMN_RAMP,
                        StationFacilityColumns.COLUMN_DISABLED_PARKING_SPOTS,
                        StationFacilityColumns.COLUMN_ELEVATED_PLATFORM,
                        StationFacilityColumns.COLUMN_ESCALATOR_UP,
                        StationFacilityColumns.COLUMN_ESCALATOR_DOWN,
                        StationFacilityColumns.COLUMN_ELEVATOR_PLATFORM,
                        StationFacilityColumns.COLUMN_HEARING_AID_SIGNAL,
                        StationFacilityColumns.COLUMN_SALES_OPEN_MONDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_MONDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_TUESDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_TUESDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_WEDNESDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_WEDNESDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_THURSDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_THURSDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_FRIDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_FRIDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_SATURDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_SATURDAY,
                        StationFacilityColumns.COLUMN_SALES_OPEN_SUNDAY,
                        StationFacilityColumns.COLUMN_SALES_CLOSE_SUNDAY,
                },
                StationFacilityColumns._ID + "=?",
                new String[]{id},
                null,
                null,
                null,
                "1");

        if (c.getCount() == 0) {
            c.close();
            db.close();
            return null;
        }

        StationFacilities result = loadFacilitiesCursor(c);
        c.close();
        db.close();
        return result;

    }

    /**
     * Load stations from a cursor. This method <strong>does not close the cursor afterwards</strong>.
     *
     * @param c The cursor from which stations should be loaded.
     * @return The array of loaded stations
     */
    private Station[] loadStationCursor(Cursor c) {
        if (c.isClosed()) {
            FirebaseCrash.logcat(SEVERE.intValue(), LOGTAG, "Tried to load closed cursor");
            return null;
        }

        if (c.getCount() == 0) {
            FirebaseCrash.logcat(SEVERE.intValue(), LOGTAG, "Tried to load cursor with 0 results!");
            return null;
        }

        c.moveToFirst();
        Station[] result = new Station[c.getCount()];
        int i = 0;
        while (!c.isAfterLast()) {

            String locale = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_stations_language", "");
            if (locale.isEmpty()) {
                // Only get locale when needed
                locale = Locale.getDefault().getISO3Language();
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("pref_stations_language", locale).apply();
            }

            String name = c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_NAME));
            String localizedName = null;

            String nl = c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_NL));
            String fr = c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_FR));
            String de = c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_DE));
            String en = c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_ALTERNATIVE_EN));

            switch (locale) {
                case "nld":
                    localizedName = nl;
                    break;
                case "fra":
                    localizedName = fr;
                    break;
                case "deu":
                    localizedName = de;
                    break;
                case "eng":
                    localizedName = en;
                    break;
            }

            if (localizedName == null || localizedName.isEmpty()) {
                localizedName = name;
            }

            Station s = new Station(
                    c.getString(c.getColumnIndex(StationsDataColumns._ID)),
                    name,
                    nl,
                    fr,
                    de,
                    en,
                    localizedName,
                    c.getString(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_COUNTRY_CODE)),
                    c.getDouble(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_LATITUDE)),
                    c.getDouble(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_LONGITUDE)),
                    c.getFloat(c.getColumnIndex(StationsDataColumns.COLUMN_NAME_AVG_STOP_TIMES)));

            c.moveToNext();
            result[i] = s;
            i++;
        }
        return result;
    }

    /**
     * Load stations from a cursor. This method <strong>does not close the cursor afterwards</strong>.
     *
     * @param c The cursor from which stations should be loaded.
     * @return The array of loaded stations
     */
    private StationFacilities loadFacilitiesCursor(Cursor c) {
        if (c.isClosed()) {
            FirebaseCrash.logcat(SEVERE.intValue(), LOGTAG, "Tried to load closed cursor");
            return null;
        }

        if (c.getCount() == 0) {
            FirebaseCrash.logcat(SEVERE.intValue(), LOGTAG, "Tried to load cursor with 0 results!");
            return null;
        }

        c.moveToFirst();

        int[][] indices = new int[][]{
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_MONDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_MONDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_TUESDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_MONDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_WEDNESDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_WEDNESDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_THURSDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_THURSDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_FRIDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_FRIDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_SATURDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_SATURDAY)},
                new int[]{c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_OPEN_SUNDAY), c.getColumnIndex(StationFacilityColumns.COLUMN_SALES_CLOSE_SUNDAY)},
        };

        LocalTime[][] openingHours = new LocalTime[7][];
        DateTimeFormatter localTimeFormatter = DateTimeFormat.forPattern("HH:mm");
        for (int i = 0; i < 7; i++) {
            if (c.getString(indices[i][0]) == null) {
                openingHours[i] = null;
            } else {
                openingHours[i] = new LocalTime[2];
                openingHours[i][0] = LocalTime.parse(c.getString(indices[i][0]), localTimeFormatter);
                openingHours[i][1] = LocalTime.parse(c.getString(indices[i][0]), localTimeFormatter);
            }
        }

        return new StationFacilities(openingHours,
                c.getString(c.getColumnIndex(StationFacilityColumns.COLUMN_STREET)),
                c.getString(c.getColumnIndex(StationFacilityColumns.COLUMN_ZIP)),
                c.getString(c.getColumnIndex(StationFacilityColumns.COLUMN_CITY)),
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_TICKET_VENDING_MACHINE)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_LUGGAGE_LOCKERS)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_FREE_PARKING)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_TAXI)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_BICYCLE_SPOTS)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_BLUE_BIKE)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_BUS)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_TRAM)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_METRO)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_WHEELCHAIR_AVAILABLE)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_RAMP)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_DISABLED_PARKING_SPOTS)),
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_ELEVATED_PLATFORM)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_ESCALATOR_UP)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_ESCALATOR_DOWN)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_ELEVATOR_PLATFORM)) == 1,
                c.getInt(c.getColumnIndex(StationFacilityColumns.COLUMN_HEARING_AID_SIGNAL)) == 1);

    }
}