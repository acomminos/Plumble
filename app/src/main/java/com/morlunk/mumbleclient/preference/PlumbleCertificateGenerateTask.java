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

package com.morlunk.mumbleclient.preference;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.widget.Toast;

import com.morlunk.mumbleclient.R;

import java.io.File;

public class PlumbleCertificateGenerateTask extends AsyncTask<Void, Void, File> {
	
	private Context context;
	private ProgressDialog loadingDialog;
	
	public PlumbleCertificateGenerateTask(Context context) {
		this.context = context;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		loadingDialog = new ProgressDialog(context);
		loadingDialog.setIndeterminate(true);
		loadingDialog.setMessage(context.getString(R.string.generateCertProgress));
		loadingDialog.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface arg0) {
				cancel(true);
				
			}
		});
		loadingDialog.show();
	}
	@Override
	protected File doInBackground(Void... params) {
		try {
			return PlumbleCertificateManager.generateCertificate();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	protected void onPostExecute(File result) {
		super.onPostExecute(result);
		if(result != null) {
			Toast.makeText(context, context.getString(R.string.generateCertSuccess, result.getName()), Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(context, R.string.generateCertFailure, Toast.LENGTH_SHORT).show();
		}
		
		loadingDialog.dismiss();
	}
}
