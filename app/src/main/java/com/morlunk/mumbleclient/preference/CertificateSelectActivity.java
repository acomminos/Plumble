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
import android.widget.ArrayAdapter;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseCertificate;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;

import java.util.List;

/**
 * Created by andrew on 11/01/16.
 */
public class CertificateSelectActivity extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private Settings mSettings;
    private List<DatabaseCertificate> mCertificates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings = Settings.getInstance(this);
        PlumbleDatabase database = new PlumbleSQLiteDatabase(this);
        mCertificates = database.getCertificates();
        database.close();

        showCertificateSelectionDialog();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DatabaseCertificate certificate = mCertificates.get(which);
        mSettings.setDefaultCertificateId(certificate.getId());
    }

    private void showCertificateSelectionDialog() {

        long defaultCertificateId = mSettings.getDefaultCertificate();
        int defaultCertificatePosition = -1;
        for (int i = 0; i < mCertificates.size(); i++) {
            if (mCertificates.get(i).getId() == defaultCertificateId) {
                defaultCertificatePosition = i;
                break;
            }
        }

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.pref_certificate_title);
        dialogBuilder.setSingleChoiceItems(
                new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice, mCertificates),
                defaultCertificatePosition, this);
        dialogBuilder.setPositiveButton(R.string.confirm, null);
        dialogBuilder.setNegativeButton(R.string.no_certificate, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mSettings.disableCertificate();
            }
        });
        AlertDialog dialog = dialogBuilder.show();
        dialog.setOnDismissListener(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
