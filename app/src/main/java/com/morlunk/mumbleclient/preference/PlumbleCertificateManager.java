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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlumbleCertificateManager {

	private static final String CERTIFICATE_FOLDER = "Plumble";
	private static final String CERTIFICATE_FORMAT = "plumble-%s.p12";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	
	/**
	 * Generates a new X.509 passwordless certificate in PKCS12 format for connection to a Mumble server.
	 * This certificate is stored in the {@value #CERTIFICATE_FOLDER} folder on the external storage, in the format {@value #CERTIFICATE_FORMAT} where the timestamp is substituted in.
	 * @return The path of the generated certificate if the operation was a success. Otherwise, null.
	 */
    public static File generateCertificate() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
        File certificateDirectory = getCertificateDirectory();

        String date = DATE_FORMAT.format(new Date());
        String certificateName = String.format(Locale.US, CERTIFICATE_FORMAT, date);
        File certificateFile = new File(certificateDirectory, certificateName);
        FileOutputStream outputStream = new FileOutputStream(certificateFile);
        JumbleCertificateGenerator.generateCertificate(outputStream);
        return certificateFile;
    }

    /**
	 * Returns a list of certificates in the {@value #CERTIFICATE_FOLDER} folder on external storage, ending with pfx or p12.
	 * @return A list of {@link File} objects containing certificates.
	 */
	public static List<File> getAvailableCertificates() throws IOException {
		File certificateDirectory = getCertificateDirectory();
		
		File[] p12Files = certificateDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith("pfx") ||
                       pathname.getName().endsWith("p12");
			}
		});
		
		return Arrays.asList(p12Files);
	}

    /**
     * Checks to see if the certificate at the given path is password protected.
     * @param certificateFile A PKCS12 certificate.
     * @return true if the certificate is password protected, false otherwise.
     */
    public static boolean isPasswordRequired(File certificateFile) throws KeyStoreException, IOException, NoSuchAlgorithmException {
        KeyStore p12store = KeyStore.getInstance("PKCS12");
        FileInputStream inputStream = new FileInputStream(certificateFile);
        try {
            p12store.load(inputStream, new char[0]);
            return false; // If loading succeeded, we can be assured that no password was required.
        } catch (IOException e) {
            e.printStackTrace();
            return true; // FIXME: this is a very coarse attempt at password detection.
        } catch (CertificateException e) {
            e.printStackTrace();
            return true; // FIXME: this is a very coarse attempt at password detection.
        }
    }

    /**
     * Checks to see if the certificate at the given path is password protected.
     * @param certificateFile A PKCS12 certificate.
     * @param password A password for the certificate.
     * @return true if the password is valid, false otherwise.
     */
    public static boolean isPasswordValid(File certificateFile, String password) throws KeyStoreException, IOException, NoSuchAlgorithmException {
        KeyStore p12store = KeyStore.getInstance("PKCS12");
        FileInputStream inputStream = new FileInputStream(certificateFile);
        try {
            p12store.load(inputStream, password.toCharArray());
            return true; // If loading succeeded, we can be assured that the password is valid
        } catch (CertificateException e) {
            e.printStackTrace();
            return false; // FIXME: this is a very coarse attempt at password detection.
        }
    }

    /**
	 * Returns the certificate directory, {@value #PLUMBLE_CERTIFICATE_FOLDER}, on external storage.
	 * Will create if does not exist, and throw an assert if the external storage is not mounted.
	 * @return The {@link File} object of the directory.
	 */
	public static File getCertificateDirectory() throws IOException {
		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File certificateDirectory = new File(Environment.getExternalStorageDirectory(), CERTIFICATE_FOLDER);
            if(!certificateDirectory.exists())
                certificateDirectory.mkdir();
            return certificateDirectory;
        } else {
            throw new IOException("External storage not available.");
        }
	}
}
