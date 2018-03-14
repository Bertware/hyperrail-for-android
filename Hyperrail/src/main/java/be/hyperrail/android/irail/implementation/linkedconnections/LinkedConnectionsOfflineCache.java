package be.hyperrail.android.irail.implementation.linkedconnections;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.joda.time.DateTime;

/**
 * Created in be.hyperrail.android.irail.implementation.LinkedConnections on 08/03/2018.
 */

public class LinkedConnectionsOfflineCache extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    // year/month/day/increment
    private static final int DATABASE_VERSION = 18030803;

    // Name of the database file
    private static final String DATABASE_NAME = "linkedconnections.db";

    // Logtag for logging purpose
    private static final String LOGTAG = "LinkedConnectionsCache";

    static final String SQL_CREATE_TABLE = "CREATE TABLE cache (_id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, data TEXT NOT NULL, datetime INTEGER)";
    static final String SQL_CREATE_INDEX = "CREATE INDEX cache_index ON cache (url);";

    public LinkedConnectionsOfflineCache(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
        db.execSQL(SQL_CREATE_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS cache;");
        onCreate(db);
    }

    public void store(String url, String data) {
        ContentValues values = new ContentValues();
        values.put("url", url);
        values.put("data", data);
        values.put("datetime", DateTime.now().getMillis());
        SQLiteDatabase db = getWritableDatabase();
        int id = (int) db.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public CachedLinkedConnections load(String url) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("cache", new String[]{"url", "data","datetime"}, "url=?", new String[]{url}, null, null, null);

        if (c.getCount() == 0){
            c.close();
            return null;
        }

        CachedLinkedConnections result = new CachedLinkedConnections();
        c.moveToFirst();
        result.createdAt = new DateTime(c.getLong(c.getColumnIndex("datetime")));
        result.data = c.getString(c.getColumnIndex("data"));
        result.url = c.getString(c.getColumnIndex("url"));
        c.close();
        return result;
    }

    public class CachedLinkedConnections {
        public String url, data;
        public DateTime createdAt;
    }
}