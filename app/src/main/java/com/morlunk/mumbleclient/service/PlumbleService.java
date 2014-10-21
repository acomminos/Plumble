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

package com.morlunk.mumbleclient.service;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.ipc.TalkBroadcastReceiver;

/**
 * An extension of the Jumble service with some added Plumble-exclusive non-standard Mumble features.
 * Created by andrew on 28/07/13.
 */
public class PlumbleService extends JumbleService implements SharedPreferences.OnSharedPreferenceChangeListener {
    /** Undocumented constant that permits a proximity-sensing wake lock. */
    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
    public static final int TTS_THRESHOLD = 250; // Maximum number of characters to read
    public static final int RECONNECT_DELAY = 10000;

    private PlumbleBinder mBinder = new PlumbleBinder();
    private Settings mSettings;
    private PlumbleNotification mNotification;
    /** Channel view overlay. */
    private PlumbleOverlay mChannelOverlay;
    /** Proximity lock for handset mode. */
    private PowerManager.WakeLock mProximityLock;
    /** Play sound when push to talk key is pressed */
    private boolean mPTTSoundEnabled;

    private TextToSpeech mTTS;
    private TextToSpeech.OnInitListener mTTSInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if(status == TextToSpeech.ERROR)
                log(Message.Type.WARNING, getString(R.string.tts_failed));
        }
    };

    /** The view representing the hot corner. */
    private PlumbleHotCorner mHotCorner;
    private PlumbleHotCorner.PlumbleHotCornerListener mHotCornerListener = new PlumbleHotCorner.PlumbleHotCornerListener() {
        @Override
        public void onHotCornerDown() {
            try {
                mBinder.setTalkingState(!mSettings.isPushToTalkToggle() || !mBinder.isTalking());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onHotCornerUp() {
            if(!mSettings.isPushToTalkToggle()) {
                try {
                    mBinder.setTalkingState(false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private BroadcastReceiver mTalkReceiver;

    private JumbleObserver mObserver = new JumbleObserver() {

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            if(reconnecting) {
                String tickerMessage = getString(R.string.reconnecting, RECONNECT_DELAY/1000);
                if (mNotification != null) {
                    mNotification.setCustomContentText(tickerMessage);
                    mNotification.setReconnecting(true);
                }
            }
        }

        @Override
        public void onUserConnected(User user) throws RemoteException {
            // Immediately request avatar upon connection.
            // FIXME: we need to refresh avatar when hash changes
            getBinder().requestAvatar(user.getSession());
        }

        @Override
        public void onUserStateUpdated(User user) throws RemoteException {
            if(user.getSession() == mBinder.getSession()) {
                mSettings.setMutedAndDeafened(user.isSelfMuted(), user.isSelfDeafened()); // Update settings mute/deafen state
                if(mNotification != null) {
                    String contentText;
                    if (user.isSelfMuted() && user.isSelfDeafened())
                        contentText = getString(R.string.status_notify_muted_and_deafened);
                    else if (user.isSelfMuted())
                        contentText = getString(R.string.status_notify_muted);
                    else
                        contentText = getString(R.string.connected);
                    mNotification.setCustomContentText(contentText);
                }
            }
        }

        @Override
        public void onMessageLogged(Message message) throws RemoteException {
            // Strip all HTML tags.
            String strippedMessage = message.getMessage().replaceAll("<[^>]*>", "");

            // Only read text messages. TODO: make this an option.
            if(message.getType() == Message.Type.TEXT_MESSAGE) {
                String formattedMessage = getString(R.string.notification_message,
                        message.getActorName(), strippedMessage);

                if(mSettings.isChatNotifyEnabled() && mNotification != null) {
                    mNotification.addMessage(formattedMessage);
                }

                // Read if TTS is enabled, the message is less than threshold, is a text message, and not deafened
                if(mSettings.isTextToSpeechEnabled() &&
                        mTTS != null &&
                        message.getType() == Message.Type.TEXT_MESSAGE &&
                        strippedMessage.length() <= TTS_THRESHOLD &&
                        getBinder().getSessionUser() != null &&
                        !getBinder().getSessionUser().isSelfDeafened()) {
                    mTTS.speak(formattedMessage, TextToSpeech.QUEUE_ADD, null);
                }
            }
        }

        @Override
        public void onPermissionDenied(String reason) throws RemoteException {
            if(mSettings.isChatNotifyEnabled() &&
                    mNotification != null) {
                mNotification.setCustomTicker(reason);
            }
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            if (isConnected() &&
                    mBinder.getSession() == user.getSession() &&
                    mBinder.getTransmitMode() == Constants.TRANSMIT_PUSH_TO_TALK &&
                    user.getTalkState() == User.TalkState.TALKING &&
                    mPTTSoundEnabled) {
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1);
            }
        }
    };
    private PlumbleNotification.OnActionListener mNotificationActionListener = new PlumbleNotification.OnActionListener() {
        @Override
        public void onMuteToggled() {
            try {
                User user = mBinder.getSessionUser();
                if (isConnected() && user != null) {
                    boolean muted = !user.isSelfMuted();
                    boolean deafened = user.isSelfDeafened() && muted;
                    mBinder.setSelfMuteDeafState(muted, deafened);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDeafenToggled() {
            try {
                User user = mBinder.getSessionUser();
                if (isConnected() && user != null) {
                    mBinder.setSelfMuteDeafState(!user.isSelfDeafened(), !user.isSelfDeafened());
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onOverlayToggled() {
            if (!mChannelOverlay.isShown()) {
                mChannelOverlay.show();
            } else {
                mChannelOverlay.hide();
            }
        }

        @Override
        public void onReconnectCanceled() {
            try {
                mBinder.cancelReconnect();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            getBinder().registerObserver(mObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Register for preference changes
        mSettings = Settings.getInstance(this);
        mPTTSoundEnabled = mSettings.isPttSoundEnabled();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        // Instantiate overlay view
        mChannelOverlay = new PlumbleOverlay(this);
        mHotCorner = new PlumbleHotCorner(this, mSettings.getHotCornerGravity(), mHotCornerListener);

        // Set up TTS
        if(mSettings.isTextToSpeechEnabled())
            mTTS = new TextToSpeech(this, mTTSInitListener);

        mTalkReceiver = new TalkBroadcastReceiver(getBinder());
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        try {
            unregisterReceiver(mTalkReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        try {
            getBinder().unregisterObserver(mObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if(mTTS != null) mTTS.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConnectionEstablished() {
        super.onConnectionEstablished();
        // Restore mute/deafen state
        if(mSettings.isMuted() || mSettings.isDeafened()) {
            try {
                getBinder().setSelfMuteDeafState(mSettings.isMuted(), mSettings.isDeafened());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onConnectionSynchronized() {
        super.onConnectionSynchronized();

        // Remove old notification left from reconnect,
        if (mNotification != null) {
            mNotification.hide();
            mNotification = null;
        }

        mNotification = PlumbleNotification.showForeground(this, mNotificationActionListener);

        registerReceiver(mTalkReceiver, new IntentFilter(TalkBroadcastReceiver.BROADCAST_TALK));

        if (mSettings.isHotCornerEnabled()) {
            mHotCorner.setShown(true);
        }
        // Configure proximity sensor
        if (mSettings.isHandsetMode()) {
            setProximitySensorOn(true);
        }
    }

    @Override
    public void onConnectionDisconnected() {
        super.onConnectionDisconnected();
        try {
            unregisterReceiver(mTalkReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // Remove overlay if present.
        mChannelOverlay.hide();

        mHotCorner.setShown(false);

        setProximitySensorOn(false);

        try {
            if(!getBinder().isReconnecting() && mNotification != null) {
                mNotification.hide();
                mNotification = null;
//                stopSelf(); // Stop manual control of the service's lifecycle.
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when the user makes a change to their preferences. Should update all preferences relevant to the service.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!isConnected()) return; // These properties should all be set on connect regardless.

        if (Settings.PREF_INPUT_METHOD.equals(key)) {
            /* Convert input method defined in settings to an integer format used by Jumble. */
            int inputMethod = mSettings.getJumbleInputMethod();
            try {
                getBinder().setTransmitMode(inputMethod);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mChannelOverlay.setPushToTalkShown(inputMethod == Constants.TRANSMIT_PUSH_TO_TALK);
        } else if (Settings.PREF_HANDSET_MODE.equals(key)) {
            setProximitySensorOn(mSettings.isHandsetMode());
        } else if (Settings.PREF_THRESHOLD.equals(key)) {
            try {
                getBinder().setVADThreshold(mSettings.getDetectionThreshold());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (Settings.PREF_HOT_CORNER_KEY.equals(key)) {
            mHotCorner.setGravity(mSettings.getHotCornerGravity());
            mHotCorner.setShown(mSettings.isHotCornerEnabled());
        } else if (Settings.PREF_USE_TTS.equals(key)) {
            if (mTTS == null && mSettings.isTextToSpeechEnabled())
                mTTS = new TextToSpeech(this, mTTSInitListener);
            else if (mTTS != null && !mSettings.isTextToSpeechEnabled()) {
                mTTS.shutdown();
                mTTS = null;
            }
        } else if (Settings.PREF_AMPLITUDE_BOOST.equals(key)) {
            try {
                getBinder().setAmplitudeBoost(mSettings.getAmplitudeBoostMultiplier());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (Settings.PREF_HALF_DUPLEX.equals(key)) {
            try {
                getBinder().setHalfDuplex(mSettings.isHalfDuplex());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (Settings.PREF_PTT_SOUND.equals(key)) {
            mPTTSoundEnabled = mSettings.isPttSoundEnabled();
        }
    }

    private void setProximitySensorOn(boolean on) {
        if(on) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mProximityLock = pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "plumble_proximity");
            mProximityLock.acquire();
        } else {
            if(mProximityLock != null) mProximityLock.release();
            mProximityLock = null;
        }
    }

    @Override
    public PlumbleBinder getBinder() {
        return mBinder;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * An extension of JumbleBinder to add Plumble-specific functionality.
     */
    public class PlumbleBinder extends JumbleBinder {
        public void setOverlayShown(boolean showOverlay) {
            if(!mChannelOverlay.isShown()) {
                mChannelOverlay.show();
            } else {
                mChannelOverlay.hide();
            }
        }

        public boolean isOverlayShown() {
            return mChannelOverlay.isShown();
        }

        public void clearChatNotifications() throws RemoteException {
            if (mNotification != null) mNotification.clearMessages();
        }

        public void cancelReconnect() throws RemoteException {
            if (mNotification != null) {
                mNotification.hide();
                mNotification = null;
            }
            super.cancelReconnect();
        }
    }
}
