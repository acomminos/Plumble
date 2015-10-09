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

package com.morlunk.mumbleclient.test;

import android.test.AndroidTestCase;

import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;

import java.util.UUID;

/**
 * Test case designed to test operations of Plumble's database.
 * A new DB is created and destroyed with each test call.
 * Created by andrew on 19/08/14.
 */
public class PlumbleSQLTestCase extends AndroidTestCase {
    /** Database name used in the active test. */
    private String mDatabaseName;
    /** Database for the active test. */
    private PlumbleSQLiteDatabase mDatabase;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDatabaseName = UUID.randomUUID().toString() + ".db";
        mDatabase = new PlumbleSQLiteDatabase(getContext(), mDatabaseName);
        mDatabase.open();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mDatabase.close();
        getContext().deleteDatabase(mDatabaseName);
    }

    public void testFavourite() {

    }

    public void testLocalMuteIgnore() {
        long server = 5;
        int userId = 1;

        // Test ignore duplicate constraint
        for (int i = 0; i < 2; i++) {
            mDatabase.addLocalMutedUser(server, userId);
            mDatabase.addLocalIgnoredUser(server, userId);
            assertEquals(1, mDatabase.getLocalMutedUsers(server).size());
            assertEquals(1, mDatabase.getLocalIgnoredUsers(server).size());
            assertEquals(userId, (int)mDatabase.getLocalMutedUsers(server).get(0));
            assertEquals(userId, (int)mDatabase.getLocalIgnoredUsers(server).get(0));
        }
        mDatabase.removeLocalMutedUser(server, userId);
        mDatabase.removeLocalIgnoredUser(server, userId);
        assertEquals(0, mDatabase.getLocalMutedUsers(server).size());
        assertEquals(0, mDatabase.getLocalIgnoredUsers(server).size());
    }
}
