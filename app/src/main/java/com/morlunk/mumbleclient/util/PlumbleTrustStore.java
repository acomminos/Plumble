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

package com.morlunk.mumbleclient.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * Created by andrew on 05/04/14.
 */
public class PlumbleTrustStore {

    private static final String STORE_FILE = "plumble-store.bks";
    private static final String STORE_PASS = "";
    private static final String STORE_FORMAT = "BKS";

    /**
     * Loads the app's trust store of certificates.
     * @return A loaded KeyStore with the user's trusted certificates.
     */
    public static KeyStore getTrustStore(Context context) throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore store = KeyStore.getInstance(STORE_FORMAT);
        try {
            FileInputStream fis = context.openFileInput(STORE_FILE);
            store.load(fis, STORE_PASS.toCharArray());
            fis.close();
        } catch (FileNotFoundException e) {
            store.load(null, null);
        }
        return store;
    }

    public static void saveTrustStore(Context context, KeyStore store) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        FileOutputStream fos = context.openFileOutput(STORE_FILE, Context.MODE_PRIVATE);
        store.store(fos, STORE_PASS.toCharArray());
        fos.close();
    }

    public static void clearTrustStore(Context context) {
        context.deleteFile(STORE_FILE);
    }

    /**
     * Gets the app's trust store path.
     * @return null if the store has not yet been initialized, or the absolute path if it has.
     */
    public static String getTrustStorePath(Context context) {
        File trustPath = new File(context.getFilesDir(), STORE_FILE);
        if(trustPath.exists()) return trustPath.getAbsolutePath();
        return null;
    }

    public static String getTrustStoreFormat() {
        return STORE_FORMAT;
    }

    public static String getTrustStorePassword() {
        return STORE_PASS;
    }
}
