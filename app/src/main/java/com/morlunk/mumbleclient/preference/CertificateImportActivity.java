/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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

package com.morlunk.mumbleclient.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.UUID;

/**
 * Created by andrew on 11/01/16.
 */
public class CertificateImportActivity extends Activity {
    public static final int REQUEST_FILE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("*/*");
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(fileIntent, REQUEST_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_FILE)
            return;

        if (resultCode == RESULT_CANCELED) {
            finish();
            return;
        }

        Uri uri = data.getData();
        InputStream is;
        try {
            is = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            // FIXME(acomminos)
            finish();
            return;
        }

        String displayName;
        Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            displayName = cursor.getString(0);
        } else {
            displayName = UUID.randomUUID().toString() + ".p12";
        }
        if (cursor != null)
            cursor.close();

        storeKeystore(new char[0], displayName, is);
    }

    private void storeKeystore(final char[] password, final String fileName, final InputStream input) {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
            keyStore.load(input, password);
        } catch (CertificateException e) {
            // A problem occurred when reading the stream; interpret this as a password being
            // required. Request a password from the user and reattempt decryption.
            // FIXME(acomminos): examine p12 file's SafeBags to determine the presence of a password
            AlertDialog.Builder promptBuilder = new AlertDialog.Builder(this);
            final EditText passwordField = new EditText(this);
            passwordField.setHint(R.string.password);
            passwordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            promptBuilder.setTitle(R.string.decrypt_certificate);
            promptBuilder.setView(passwordField);
            promptBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
            promptBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    storeKeystore(passwordField.getText().toString().toCharArray(), fileName, input);
                }
            });
            promptBuilder.show();
            return;
        } catch (KeyStoreException|IOException|NoSuchAlgorithmException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.invalid_certificate, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            keyStore.store(output, new char[0]);
        } catch (KeyStoreException|IOException|NoSuchAlgorithmException|CertificateException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.certificate_load_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        PlumbleDatabase database = new PlumbleSQLiteDatabase(this);
        database.addCertificate(fileName, output.toByteArray());
        database.close();

        Toast.makeText(this, getString(R.string.certificate_import_success, fileName),
                       Toast.LENGTH_LONG).show();
        finish();
    }
}
