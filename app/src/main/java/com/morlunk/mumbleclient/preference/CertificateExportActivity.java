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
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.DatabaseCertificate;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by andrew on 12/01/16.
 */
public class CertificateExportActivity extends Activity implements DialogInterface.OnClickListener {
    /**
     * The name of the directory to export to on external storage.
     */
    private static final String EXTERNAL_STORAGE_DIR = "Plumble";

    private PlumbleDatabase mDatabase;
    private List<DatabaseCertificate> mCertificates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDatabase = new PlumbleSQLiteDatabase(this);
        mCertificates = mDatabase.getCertificates();

        CharSequence[] labels = new CharSequence[mCertificates.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = mCertificates.get(i).getName();
        }

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(R.string.pref_export_certificate_title);
        adb.setItems(labels, this);
        adb.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        adb.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDatabase.close();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DatabaseCertificate certificate = mCertificates.get(which);
        saveCertificate(certificate);
    }

    private void saveCertificate(DatabaseCertificate certificate) {
        byte[] data = mDatabase.getCertificateData(certificate.getId());
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            showErrorDialog(R.string.externalStorageUnavailable);
            return;
        }

        File storageDirectory = Environment.getExternalStorageDirectory();
        File plumbleDirectory = new File(storageDirectory, EXTERNAL_STORAGE_DIR);
        if (!plumbleDirectory.exists() && !plumbleDirectory.mkdir()) {
            showErrorDialog(R.string.externalStorageUnavailable);
            return;
        }
        File outputFile = new File(plumbleDirectory, certificate.getName());
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(data);
            bos.close();

            Toast.makeText(this, getString(R.string.export_success, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
            finish();
        } catch (FileNotFoundException e) {
            showErrorDialog(R.string.externalStorageUnavailable);
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog(R.string.error_writing_to_storage);
        }
    }

    private void showErrorDialog(int resourceId) {
        AlertDialog.Builder errorDialog = new AlertDialog.Builder(this);
        errorDialog.setMessage(resourceId);
        errorDialog.setPositiveButton(android.R.string.ok, null);
        errorDialog.show();
    }
}
