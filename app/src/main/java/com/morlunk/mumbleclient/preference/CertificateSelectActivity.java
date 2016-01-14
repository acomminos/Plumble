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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrew on 11/01/16.
 */
public class CertificateSelectActivity extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private List<ICertificateItem> mCertificates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings settings = Settings.getInstance(this);
        mCertificates = new ArrayList<>();
        mCertificates.add(new NoCertificateItem(getString(R.string.no_certificate), settings));
        PlumbleDatabase database = new PlumbleSQLiteDatabase(this);
        for (DatabaseCertificate certificate : database.getCertificates()) {
            mCertificates.add(new CertificateItem(certificate, settings));
        }
        database.close();

        showCertificateSelectionDialog();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        ICertificateItem certificate = mCertificates.get(which);
        certificate.onActivate();
        finish();
    }

    private void showCertificateSelectionDialog() {
        int defaultCertificatePosition = -1;
        for (int i = 0; i < mCertificates.size(); i++) {
            if (mCertificates.get(i).isDefault()) {
                defaultCertificatePosition = i;
                break;
            }
        }

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.pref_certificate_title);
        dialogBuilder.setSingleChoiceItems(
                new ArrayAdapter<>(this, R.layout.list_certificate_item, mCertificates),
                defaultCertificatePosition, this);
        dialogBuilder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = dialogBuilder.show();
        dialog.setOnDismissListener(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    private interface ICertificateItem {
        void onActivate();
        boolean isDefault();
    }

    private static class CertificateItem implements ICertificateItem {
        private final DatabaseCertificate mCertificate;
        private final Settings mSettings;

        public CertificateItem(DatabaseCertificate certificate, Settings settings) {
            mCertificate = certificate;
            mSettings = settings;
        }

        @Override
        public void onActivate() {
            mSettings.setDefaultCertificateId(mCertificate.getId());
        }

        @Override
        public boolean isDefault() {
            return mSettings.getDefaultCertificate() == mCertificate.getId();
        }

        @Override
        public String toString() {
            return mCertificate.getName();
        }
    }

    private static class NoCertificateItem implements ICertificateItem {
        private final String mText;
        private final Settings mSettings;

        public NoCertificateItem(String text, Settings settings) {
            mText = text;
            mSettings = settings;
        }

        @Override
        public void onActivate() {
            mSettings.disableCertificate();
        }

        @Override
        public boolean isDefault() {
            return !mSettings.isUsingCertificate();
        }

        @Override
        public String toString() {
            return mText;
        }
    }
}
