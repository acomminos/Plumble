/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.Constants;

import java.util.ArrayList;
import java.util.List;

public class PlumbleSQLiteDatabase extends SQLiteOpenHelper implements PlumbleDatabase {
    public static final String DATABASE_NAME = "mumble.db";

    public static final String TABLE_SERVER = "server";
    public static final String SERVER_ID = "_id";
    public static final String SERVER_NAME = "name";
    public static final String SERVER_HOST = "host";
    public static final String SERVER_PORT = "port";
    public static final String SERVER_USERNAME = "username";
    public static final String SERVER_PASSWORD = "password";
    public static final String TABLE_SERVER_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_SERVER + "` ("
            + "`" + SERVER_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + SERVER_NAME + "` TEXT NOT NULL,"
            + "`" + SERVER_HOST + "` TEXT NOT NULL,"
            + "`" + SERVER_PORT + "` INTEGER,"
            + "`" + SERVER_USERNAME + "` TEXT NOT NULL,"
            + "`" + SERVER_PASSWORD + "` TEXT"
            + ");";

    public static final String TABLE_FAVOURITES = "favourites";
    public static final String FAVOURITES_ID = "_id";
    public static final String FAVOURITES_CHANNEL = "channel";
    public static final String FAVOURITES_SERVER = "server";
    public static final String TABLE_FAVOURITES_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_FAVOURITES + "` ("
            + "`" + FAVOURITES_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + FAVOURITES_CHANNEL + "` TEXT NOT NULL,"
            + "`" + FAVOURITES_SERVER + "` INTEGER NOT NULL"
            + ");";

    public static final String TABLE_TOKENS = "tokens";
    public static final String TOKENS_ID = "_id";
    public static final String TOKENS_VALUE = "value";
    public static final String TOKENS_SERVER = "server";
    public static final String TABLE_TOKENS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_TOKENS + "` ("
            + "`" + TOKENS_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + TOKENS_VALUE + "` TEXT NOT NULL,"
            + "`" + TOKENS_SERVER + "` INTEGER NOT NULL"
            + ");";

    public static final String TABLE_COMMENTS = "comments";
    public static final String COMMENTS_WHO = "who";
    public static final String COMMENTS_COMMENT = "comment";
    public static final String COMMENTS_SEEN = "seen";
    public static final String TABLE_COMMENTS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_COMMENTS + "` ("
            + "`" + COMMENTS_WHO + "` TEXT NOT NULL,"
            + "`" + COMMENTS_COMMENT + "` TEXT NOT NULL,"
            + "`" + COMMENTS_SEEN + "` DATE NOT NULL"
            + ");";

    public static final String TABLE_LOCAL_MUTE = "local_mute";
    public static final String LOCAL_MUTE_SERVER = "server";
    public static final String LOCAL_MUTE_USER = "user";
    public static final String TABLE_LOCAL_MUTE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE_LOCAL_MUTE + " ("
            + "`" + LOCAL_MUTE_SERVER +"` INTEGER NOT NULL,"
            + "`" + LOCAL_MUTE_USER + "` INTEGER NOT NULL,"
            + "CONSTRAINT server_user UNIQUE(" + LOCAL_MUTE_SERVER + "," + LOCAL_MUTE_USER + ")"
            + ");";

    public static final String TABLE_LOCAL_IGNORE = "local_ignore";
    public static final String LOCAL_IGNORE_SERVER = "server";
    public static final String LOCAL_IGNORE_USER = "user";
    public static final String TABLE_LOCAL_IGNORE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE_LOCAL_IGNORE + " ("
            + "`" + LOCAL_IGNORE_SERVER +"` INTEGER NOT NULL,"
            + "`" + LOCAL_IGNORE_USER + "` INTEGER NOT NULL,"
            + "CONSTRAINT server_user UNIQUE(" + LOCAL_IGNORE_SERVER + "," + LOCAL_IGNORE_USER + ")"
            + ");";

    public static final String TABLE_CERTIFICATES = "certificates";
    public static final String COLUMN_CERTIFICATES_ID = "_id";
    public static final String COLUMN_CERTIFICATES_DATA = "data";
    public static final String COLUMN_CERTIFICATES_NAME = "name";
    public static final String TABLE_CERTIFICATES_CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE_CERTIFICATES + " ("
            + "`" + COLUMN_CERTIFICATES_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + COLUMN_CERTIFICATES_DATA + "` BLOB NOT NULL,"
            + "`" + COLUMN_CERTIFICATES_NAME + "` TEXT NOT NULL"
            + ");";

    public static final Integer PRE_FAVOURITES_DB_VERSION = 2;
    public static final Integer PRE_TOKENS_DB_VERSION = 3;
    public static final Integer PRE_COMMENTS_DB_VERSION = 4;
    public static final Integer PRE_LOCAL_MUTE_DB_VERSION = 5;
    public static final Integer PRE_LOCAL_IGNORE_DB_VERSION = 6;
    public static final Integer PRE_CERTIFICATES_DB_VERSION = 7;
    public static final Integer CURRENT_DB_VERSION = 8;

    public PlumbleSQLiteDatabase(Context context) {
        super(context, DATABASE_NAME, null, CURRENT_DB_VERSION);
    }

    public PlumbleSQLiteDatabase(Context context, String name) {
        super(context, name, null, CURRENT_DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_SERVER_CREATE_SQL);
        db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
        db.execSQL(TABLE_TOKENS_CREATE_SQL);
        db.execSQL(TABLE_COMMENTS_CREATE_SQL);
        db.execSQL(TABLE_LOCAL_MUTE_CREATE_SQL);
        db.execSQL(TABLE_LOCAL_IGNORE_CREATE_SQL);
        db.execSQL(TABLE_CERTIFICATES_CREATE_SQL);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {
        Log.w(Constants.TAG, "Database upgrade from " + oldVersion + " to " + newVersion);
        if (oldVersion <= PRE_FAVOURITES_DB_VERSION) {
            db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
        }

        if (oldVersion <= PRE_TOKENS_DB_VERSION) {
            db.execSQL(TABLE_TOKENS_CREATE_SQL);
        }

        if (oldVersion <= PRE_COMMENTS_DB_VERSION) {
            db.execSQL(TABLE_COMMENTS_CREATE_SQL);
        }

        if (oldVersion <= PRE_LOCAL_MUTE_DB_VERSION) {
            db.execSQL(TABLE_LOCAL_MUTE_CREATE_SQL);
        }

        if (oldVersion <= PRE_LOCAL_IGNORE_DB_VERSION) {
            db.execSQL(TABLE_LOCAL_IGNORE_CREATE_SQL);
        }

        if (oldVersion <= PRE_CERTIFICATES_DB_VERSION) {
            db.execSQL(TABLE_CERTIFICATES_CREATE_SQL);
        }
    }

    @Override
    public void open() {
        // Do nothing. Database will be opened automatically when accessing it.
    }

    @Override
    public List<Server> getServers() {
        Cursor c = getReadableDatabase().query(
                TABLE_SERVER,
                new String[]{SERVER_ID, SERVER_NAME, SERVER_HOST,
                        SERVER_PORT, SERVER_USERNAME, SERVER_PASSWORD},
                null,
                null,
                null,
                null,
                null);

        List<Server> servers = new ArrayList<Server>();

        c.moveToFirst();
        while (!c.isAfterLast()) {
            Server server = new Server(c.getInt(c.getColumnIndex(SERVER_ID)),
                    c.getString(c.getColumnIndex(SERVER_NAME)),
                    c.getString(c.getColumnIndex(SERVER_HOST)),
                    c.getInt(c.getColumnIndex(SERVER_PORT)),
                    c.getString(c.getColumnIndex(SERVER_USERNAME)),
                    c.getString(c.getColumnIndex(SERVER_PASSWORD)));
            servers.add(server);
            c.moveToNext();
        }

        c.close();

        return servers;
    }

    @Override
    public void addServer(Server server) {
        ContentValues values = new ContentValues();
        values.put(SERVER_NAME, server.getName());
        values.put(SERVER_HOST, server.getHost());
        values.put(SERVER_PORT, server.getPort());
        values.put(SERVER_USERNAME, server.getUsername());
        values.put(SERVER_PASSWORD, server.getPassword());

        server.setId(getWritableDatabase().insert(TABLE_SERVER, null, values));
    }

    @Override
    public void updateServer(Server server) {
        ContentValues values = new ContentValues();
        values.put(SERVER_NAME, server.getName());
        values.put(SERVER_HOST, server.getHost());
        values.put(SERVER_PORT, server.getPort());
        values.put(SERVER_USERNAME, server.getUsername());
        values.put(SERVER_PASSWORD, server.getPassword());
        getWritableDatabase().update(
                TABLE_SERVER,
                values,
                SERVER_ID + "=?",
                new String[]{Long.toString(server.getId())});
    }

    @Override
    public void removeServer(Server server) {
        getWritableDatabase().delete(TABLE_SERVER, SERVER_ID + "=?",
                new String[] { String.valueOf(server.getId()) });
        // Clean up server-specific entries
        getWritableDatabase().delete(TABLE_FAVOURITES, FAVOURITES_SERVER + "=?",
                new String[] { String.valueOf(server.getId()) });
        getWritableDatabase().delete(TABLE_TOKENS, TOKENS_SERVER + "=?",
                new String[] { String.valueOf(server.getId()) });
        getWritableDatabase().delete(TABLE_LOCAL_MUTE, LOCAL_MUTE_SERVER + "=?",
                new String[] { String.valueOf(server.getId()) });
        getWritableDatabase().delete(TABLE_LOCAL_IGNORE, LOCAL_IGNORE_SERVER + "=?",
                new String[]{String.valueOf(server.getId())});
    }

    public List<Integer> getPinnedChannels(long serverId) {

        final Cursor c = getReadableDatabase().query(
                TABLE_FAVOURITES,
                new String[]{FAVOURITES_CHANNEL},
                FAVOURITES_SERVER + "=?",
                new String[]{String.valueOf(serverId)},
                null,
                null,
                null);

        List<Integer> favourites = new ArrayList<Integer>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            favourites.add(c.getInt(0));
            c.moveToNext();
        }

        c.close();

        return favourites;
    }

    @Override
    public void addPinnedChannel(long serverId, int channelId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(FAVOURITES_CHANNEL, channelId);
        contentValues.put(FAVOURITES_SERVER, serverId);
        getWritableDatabase().insert(TABLE_FAVOURITES, null, contentValues);
    }

    @Override
    public boolean isChannelPinned(long serverId, int channelId) {
        Cursor c = getReadableDatabase().query(
                TABLE_FAVOURITES,
                new String[]{FAVOURITES_CHANNEL},
                FAVOURITES_SERVER + "=? AND " +
                FAVOURITES_CHANNEL + "=?",
                new String[]{String.valueOf(serverId), String.valueOf(channelId)},
                null,
                null,
                null);
        c.moveToFirst();
        return !c.isAfterLast();
    }

    public void removePinnedChannel(long serverId, int channelId) {
        getWritableDatabase().delete(TABLE_FAVOURITES, "server = ? AND channel = ?", new String[] { Long.toString(serverId), Integer.toString(channelId)});
    }

    @Override
    public List<String> getAccessTokens(long serverId) {
        Cursor cursor = getReadableDatabase().query(TABLE_TOKENS, new String[] { TOKENS_VALUE }, TOKENS_SERVER+"=?", new String[] { String.valueOf(serverId) }, null, null, null);
        cursor.moveToFirst();
        List<String> tokens = new ArrayList<String>();
        while(!cursor.isAfterLast()) {
            tokens.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return tokens;
    }

    @Override
    public void addAccessToken(long serverId, String token) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TOKENS_SERVER, serverId);
        contentValues.put(TOKENS_VALUE, token);
        getWritableDatabase().insert(TABLE_TOKENS, null, contentValues);
    }

    @Override
    public void removeAccessToken(long serverId, String token) {
        getWritableDatabase().delete(TABLE_TOKENS, TOKENS_SERVER+"=? AND "+TOKENS_VALUE+"=?", new String[] {String.valueOf(serverId), token });
    }

    @Override
    public List<Integer> getLocalMutedUsers(long serverId) {
        Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_MUTE,
                new String[] { LOCAL_MUTE_USER },
                LOCAL_MUTE_SERVER + "=?",
                new String[] { String.valueOf(serverId) },
                null, null, null);
        cursor.moveToNext();
        List<Integer> users = new ArrayList<Integer>();
        while (!cursor.isAfterLast()) {
            users.add(cursor.getInt(0));
            cursor.moveToNext();
        }
        return users;
    }

    @Override
    public void addLocalMutedUser(long serverId, int userId) {
        ContentValues values = new ContentValues();
        values.put(LOCAL_MUTE_SERVER, serverId);
        values.put(LOCAL_MUTE_USER, userId);
        getWritableDatabase().insert(TABLE_LOCAL_MUTE, null, values);
    }

    @Override
    public void removeLocalMutedUser(long serverId, int userId) {
        getWritableDatabase().delete(TABLE_LOCAL_MUTE,
                LOCAL_MUTE_SERVER + "=? AND " + LOCAL_MUTE_USER + "=?",
                new String[] { String.valueOf(serverId), String.valueOf(userId) });
    }

    @Override
    public List<Integer> getLocalIgnoredUsers(long serverId) {
        Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_IGNORE,
                new String[] { LOCAL_IGNORE_USER },
                LOCAL_IGNORE_SERVER + "=?",
                new String[] { String.valueOf(serverId) },
                null, null, null);
        cursor.moveToFirst();
        List<Integer> users = new ArrayList<Integer>();
        while (!cursor.isAfterLast()) {
            users.add(cursor.getInt(0));
            cursor.moveToNext();
        }
        return users;
    }

    @Override
    public void addLocalIgnoredUser(long serverId, int userId) {
        ContentValues values = new ContentValues();
        values.put(LOCAL_IGNORE_SERVER, serverId);
        values.put(LOCAL_IGNORE_USER, userId);
        getWritableDatabase().insert(TABLE_LOCAL_IGNORE, null, values);
    }

    @Override
    public void removeLocalIgnoredUser(long serverId, int userId) {
        getWritableDatabase().delete(TABLE_LOCAL_IGNORE,
                LOCAL_IGNORE_SERVER + "=? AND " + LOCAL_IGNORE_USER + "=?",
                new String[] { String.valueOf(serverId), String.valueOf(userId) });
    }

    @Override
    public DatabaseCertificate addCertificate(String name, byte[] certificate) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CERTIFICATES_NAME, name);
        values.put(COLUMN_CERTIFICATES_DATA, certificate);
        long id = getWritableDatabase().insert(TABLE_CERTIFICATES, null, values);
        return new DatabaseCertificate(id, name);
    }

    @Override
    public List<DatabaseCertificate> getCertificates() {
        Cursor cursor = getReadableDatabase().query(TABLE_CERTIFICATES,
                new String[] { COLUMN_CERTIFICATES_ID, COLUMN_CERTIFICATES_NAME },
                null, null, null, null, null);
        List<DatabaseCertificate> certificates = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            certificates.add(new DatabaseCertificate(cursor.getLong(0), cursor.getString(1)));
            cursor.moveToNext();
        }
        cursor.close();
        return certificates;
    }

    @Override
    public byte[] getCertificateData(long id) {
        Cursor cursor = getReadableDatabase().query(TABLE_CERTIFICATES,
                new String[] { COLUMN_CERTIFICATES_DATA },
                COLUMN_CERTIFICATES_ID + "=?",
                new String[] { String.valueOf(id) }, null, null, null);
        if (!cursor.moveToFirst())
            return null;
        byte[] data = cursor.getBlob(0);
        cursor.close();
        return data;
    }

    @Override
    public void removeCertificate(long id) {
        getWritableDatabase().delete(TABLE_CERTIFICATES,
                COLUMN_CERTIFICATES_ID + "=?",
                new String[] { String.valueOf(id) });
    }

    @Override
    public boolean isCommentSeen(String hash, byte[] commentHash) {
        Cursor cursor = getReadableDatabase().query(TABLE_COMMENTS,
                new String[]{COMMENTS_WHO, COMMENTS_COMMENT, COMMENTS_SEEN}, COMMENTS_WHO + "=? AND " + COMMENTS_COMMENT + "=?",
                new String[]{hash, new String(commentHash)},
                null, null, null);
        boolean hasNext = cursor.moveToNext();
        cursor.close();
        return hasNext;
    }

    @Override
    public void markCommentSeen(String hash, byte[] commentHash) {
        ContentValues values = new ContentValues();
        values.put(COMMENTS_WHO, hash);
        values.put(COMMENTS_COMMENT, commentHash);
        values.put(COMMENTS_SEEN, "datetime('now')");
        getWritableDatabase().replace(TABLE_COMMENTS, null, values);
    }
}
