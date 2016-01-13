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

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseCertificate;

/**
 * Created by andrew on 12/01/16.
 */
public class CertificateGenerateActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PlumbleCertificateGenerateTask task = new PlumbleCertificateGenerateTask(this) {
            @Override
            protected void onPostExecute(DatabaseCertificate result) {
                super.onPostExecute(result);
                if (result == null) {
                    finish();
                    return;
                }

                Settings settings = Settings.getInstance(CertificateGenerateActivity.this);
                settings.setDefaultCertificateId(result.getId());
                showCompletionDialog(result);
            }
        };
        task.execute();
    }

    private void showCompletionDialog(DatabaseCertificate result) {
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setMessage(getString(R.string.generateCertSuccess, result.getName()));
        adb.setPositiveButton(android.R.string.ok, null);
        adb.show().setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
    }

}
