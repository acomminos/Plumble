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

import android.os.Environment;

import com.morlunk.jumble.net.JumbleCertificateGenerator;

import org.spongycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PlumbleCertificateManager {

	private static final String PLUMBLE_CERTIFICATE_FOLDER = "Plumble";
	private static final String PLUMBLE_CERTIFICATE_FORMAT = "plumble-%d.p12";
	private static final String PLUMBLE_CERTIFICATE_EXTENSION = "p12";
	
	/**
	 * Generates a new X.509 passwordless certificate in PKCS12 format for connection to a Mumble server.
	 * This certificate is stored in the {@value #PLUMBLE_CERTIFICATE_FOLDER} folder on the external storage, in the format {@value #PLUMBLE_CERTIFICATE_FORMAT} where the timestamp is substituted in.
	 * @return The path of the generated certificate if the operation was a success. Otherwise, null.
	 */
	public static File generateCertificate() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
            File certificateDirectory = getCertificateDirectory();
                
            String certificateName = String.format(Locale.US, PLUMBLE_CERTIFICATE_FORMAT, System.currentTimeMillis() / 1000L);
            File certificateFile = new File(certificateDirectory, certificateName);
            FileOutputStream outputStream = new FileOutputStream(certificateFile);
            JumbleCertificateGenerator.generateCertificate(outputStream);
            return certificateFile;
	}
	
	/**
	 * Returns a list of certificates in the {@value #PLUMBLE_CERTIFICATE_FOLDER} folder on external storage, ending with {@value #PLUMBLE_CERTIFICATE_EXTENSION}.
	 * @return A list of {@link File} objects containing certificates.
	 */
	public static List<File> getAvailableCertificates() {
		File certificateDirectory = getCertificateDirectory();
		
		File[] p12Files = certificateDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(PLUMBLE_CERTIFICATE_EXTENSION);
			}
		});
		
		return Arrays.asList(p12Files);
	}
	
	/**
	 * Returns the certificate directory, {@value #PLUMBLE_CERTIFICATE_FOLDER}, on external storage.
	 * Will create if does not exist, and throw an assert if the external storage is not mounted.
	 * @return The {@link File} object of the directory.
	 */
	public static File getCertificateDirectory() {
		assert Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
		File certificateDirectory = new File(Environment.getExternalStorageDirectory(), PLUMBLE_CERTIFICATE_FOLDER);
		if(!certificateDirectory.exists())
			certificateDirectory.mkdir();
		return certificateDirectory;
	}
}
