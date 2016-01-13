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

package com.morlunk.mumbleclient.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;

import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.service.PlumbleService;
import com.morlunk.mumbleclient.util.PlumbleTrustStore;

import java.util.ArrayList;

/**
 * Constructs an intent for connection to a PlumbleService and executes it.
 * Created by andrew on 20/08/14.
 */
public class ServerConnectTask extends AsyncTask<Server, Void, Intent> {
    private Context mContext;
    private PlumbleDatabase mDatabase;
    private Settings mSettings;

    public ServerConnectTask(Context context, PlumbleDatabase database) {
        mContext = context;
        mDatabase = database;
        mSettings = Settings.getInstance(context);
    }

    @Override
    protected Intent doInBackground(Server... params) {
        Server server = params[0];

        /* Convert input method defined in settings to an integer format used by Jumble. */
        int inputMethod = mSettings.getJumbleInputMethod();

        int audioSource = mSettings.isHandsetMode() ?
                MediaRecorder.AudioSource.DEFAULT : MediaRecorder.AudioSource.MIC;
        int audioStream = mSettings.isHandsetMode() ?
                AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;

        String applicationVersion = "";
        try {
            applicationVersion = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Intent connectIntent = new Intent(mContext, PlumbleService.class);
        connectIntent.putExtra(JumbleService.EXTRAS_SERVER, server);
        connectIntent.putExtra(JumbleService.EXTRAS_CLIENT_NAME, mContext.getString(R.string.app_name)+" "+applicationVersion);
        connectIntent.putExtra(JumbleService.EXTRAS_TRANSMIT_MODE, inputMethod);
        connectIntent.putExtra(JumbleService.EXTRAS_DETECTION_THRESHOLD, mSettings.getDetectionThreshold());
        connectIntent.putExtra(JumbleService.EXTRAS_AMPLITUDE_BOOST, mSettings.getAmplitudeBoostMultiplier());
        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, mSettings.isAutoReconnectEnabled());
        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, PlumbleService.RECONNECT_DELAY);
        connectIntent.putExtra(JumbleService.EXTRAS_USE_OPUS, !mSettings.isOpusDisabled());
        connectIntent.putExtra(JumbleService.EXTRAS_INPUT_RATE, mSettings.getInputSampleRate());
        connectIntent.putExtra(JumbleService.EXTRAS_INPUT_QUALITY, mSettings.getInputQuality());
        connectIntent.putExtra(JumbleService.EXTRAS_FORCE_TCP, mSettings.isTcpForced());
        connectIntent.putExtra(JumbleService.EXTRAS_USE_TOR, mSettings.isTorEnabled());
        connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, (ArrayList<String>) mDatabase.getAccessTokens(server.getId()));
        connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_SOURCE, audioSource);
        connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_STREAM, audioStream);
        connectIntent.putExtra(JumbleService.EXTRAS_FRAMES_PER_PACKET, mSettings.getFramesPerPacket());
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE, PlumbleTrustStore.getTrustStorePath(mContext));
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_PASSWORD, PlumbleTrustStore.getTrustStorePassword());
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_FORMAT, PlumbleTrustStore.getTrustStoreFormat());
        connectIntent.putExtra(JumbleService.EXTRAS_HALF_DUPLEX, mSettings.isHalfDuplex());
        connectIntent.putExtra(JumbleService.EXTRAS_ENABLE_PREPROCESSOR, mSettings.isPreprocessorEnabled());
        if (server.isSaved()) {
            ArrayList<Integer> muteHistory = (ArrayList<Integer>) mDatabase.getLocalMutedUsers(server.getId());
            ArrayList<Integer> ignoreHistory = (ArrayList<Integer>) mDatabase.getLocalIgnoredUsers(server.getId());
            connectIntent.putExtra(JumbleService.EXTRAS_LOCAL_MUTE_HISTORY, muteHistory);
            connectIntent.putExtra(JumbleService.EXTRAS_LOCAL_IGNORE_HISTORY, ignoreHistory);
        }

        if (mSettings.isUsingCertificate()) {
            long certificateId = mSettings.getDefaultCertificate();
            byte[] certificate = mDatabase.getCertificateData(certificateId);
            if (certificate != null)
                connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE, certificate);
            // TODO(acomminos): handle the case where a certificate's data is unavailable.
        }

        connectIntent.setAction(JumbleService.ACTION_CONNECT);
        return connectIntent;
    }

    @Override
    protected void onPostExecute(Intent intent) {
        super.onPostExecute(intent);
        mContext.startService(intent);
    }
}
