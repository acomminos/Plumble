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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.app.PlumbleActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * An extension of the Jumble service with some added Plumble-exclusive non-standard Mumble features.
 * Created by andrew on 28/07/13.
 */
public class PlumbleService extends JumbleService implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String BROADCAST_TALK = "com.morlunk.mumbleclient.action.TALK";
    public static final String EXTRA_TALK_STATUS = "status";
    public static final String TALK_STATUS_ON = "on";
    public static final String TALK_STATUS_OFF = "off";
    public static final String TALK_STATUS_TOGGLE = "toggle";

    /** Undocumented constant that permits a proximity-sensing wake lock. */
    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
    public static final int TTS_THRESHOLD = 250; // Maximum number of characters to read

    public static final String BROADCAST_MUTE = "broadcast_mute";
    public static final String BROADCAST_DEAFEN = "broadcast_deafen";
    public static final String BROADCAST_TOGGLE_OVERLAY = "broadcast_toggle_overlay";

    public static final int STATUS_NOTIFICATION_ID = 1;

    private PlumbleBinder mBinder = new PlumbleBinder();
    private NotificationCompat.Builder mStatusNotificationBuilder;
    /** A list of messages to be displayed in the chat notification. Should be cleared when notification pressed. */
    private List<String> mUnreadMessages = new ArrayList<String>();

    private Settings mSettings;
    /** Channel view overlay. */
    private PlumbleOverlay mChannelOverlay;
    /** Proximity lock for handset mode. */
    private PowerManager.WakeLock mProximityLock;

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

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!isConnected()) return;
            try {
                if(BROADCAST_MUTE.equals(intent.getAction())) {
                    User user = getBinder().getSessionUser();
                    boolean muted = !user.isSelfMuted();
                    boolean deafened = user.isSelfDeafened();
                    deafened &= muted;
                    getBinder().setSelfMuteDeafState(muted, deafened);
                } else if(BROADCAST_DEAFEN.equals(intent.getAction())) {
                    User user = getBinder().getSessionUser();
                    getBinder().setSelfMuteDeafState(!user.isSelfDeafened(), !user.isSelfDeafened());
                } else if(BROADCAST_TOGGLE_OVERLAY.equals(intent.getAction())) {
                    if(!mChannelOverlay.isShown())
                        mChannelOverlay.show();
                    else
                        mChannelOverlay.hide();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private BroadcastReceiver mTalkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isConnected()) return;
            try {
                if (BROADCAST_TALK.equals(intent.getAction())) {
                    String status = intent.getStringExtra(EXTRA_TALK_STATUS);
                    if (status == null) status = TALK_STATUS_TOGGLE;
                    if (TALK_STATUS_ON.equals(status)) {
                        mBinder.setTalkingState(true);
                    } else if (TALK_STATUS_OFF.equals(status)) {
                        mBinder.setTalkingState(false);
                    } else if (TALK_STATUS_TOGGLE.equals(status)) {
                        mBinder.setTalkingState(!mBinder.isTalking());
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private JumbleObserver mObserver = new JumbleObserver() {

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            if(reconnecting) {
                String tickerMessage = getString(R.string.reconnecting, PlumbleActivity.RECONNECT_DELAY/1000);
                if(mNotificationReceiver == null) createNotification();
                updateNotificationTicker(tickerMessage);
                updateNotificationState();
            }
        }

        @Override
        public void onUserStateUpdated(User user) throws RemoteException {
            if(user.getSession() == mBinder.getSession()) {
                mSettings.setMutedAndDeafened(user.isSelfMuted(), user.isSelfDeafened()); // Update settings mute/deafen state
                updateNotificationState();
            }
        }

        @Override
        public void onMessageLogged(Message message) throws RemoteException {
            // Strip all HTML tags.
            String strippedMessage = message.getMessage().replaceAll("<[^>]*>", "");

            // Only read text messages. TODO: make this an option.
            if(message.getType() == Message.Type.TEXT_MESSAGE) {
                User actor = getBinder().getUser(message.getActor());
                String actorName = actor != null ? actor.getName() : getString(R.string.server);

                String formattedMessage = actorName + ": " + strippedMessage;
                mUnreadMessages.add(formattedMessage);

                if(mSettings.isChatNotifyEnabled() && mStatusNotificationBuilder != null) {
                    // Set the ticker to be the last message received
                    mStatusNotificationBuilder.setTicker(mUnreadMessages.get(mUnreadMessages.size() - 1));
                    updateNotificationState();
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
            if(!mSettings.isChatNotifyEnabled()) return;
            updateNotificationTicker(reason);
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            if (mBinder.getSession() == user.getSession()) {
                if (mBinder.getTransmitMode() == Constants.TRANSMIT_PUSH_TO_TALK) {
                    if (user.getTalkState() == User.TalkState.TALKING) {
                        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                        audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Register for notification actions
        IntentFilter notificationIntentFilter = new IntentFilter();
        notificationIntentFilter.addAction(BROADCAST_MUTE);
        notificationIntentFilter.addAction(BROADCAST_DEAFEN);
        notificationIntentFilter.addAction(BROADCAST_TOGGLE_OVERLAY);
        registerReceiver(mNotificationReceiver, notificationIntentFilter);
        registerReceiver(mTalkReceiver, new IntentFilter(BROADCAST_TALK));

        try {
            getBinder().registerObserver(mObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Register for preference changes
        mSettings = Settings.getInstance(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        // Instantiate overlay view
        mChannelOverlay = new PlumbleOverlay(this);

        mHotCorner = new PlumbleHotCorner(this, mSettings.getHotCornerGravity(), mHotCornerListener);

        // Set up TTS
        if(mSettings.isTextToSpeechEnabled())
            mTTS = new TextToSpeech(this, mTTSInitListener);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mNotificationReceiver);
        unregisterReceiver(mTalkReceiver);
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
        createNotification();

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
        // Remove overlay if present.
        mChannelOverlay.hide();

        mHotCorner.setShown(false);

        setProximitySensorOn(false);

        try {
            if(!getBinder().isReconnecting()) {
                hideNotification();
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
        }
    }

    /**
     * Creates a new NotificationCompat.Builder configured for the service, and shows it.
     */
    public void createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setTicker(getResources().getString(R.string.plumbleConnected));
        builder.setContentTitle(getResources().getString(R.string.app_name));
        builder.setContentText(getResources().getString(R.string.connected));
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOngoing(true);

        // Add notification triggers
        Intent muteIntent = new Intent(BROADCAST_MUTE);
        Intent deafenIntent = new Intent(BROADCAST_DEAFEN);
        Intent overlayIntent = new Intent(BROADCAST_TOGGLE_OVERLAY);

        if(isConnected()) {
            builder.addAction(R.drawable.ic_action_microphone,
                    getString(R.string.mute), PendingIntent.getBroadcast(this, 1,
                    muteIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_audio,
                    getString(R.string.deafen), PendingIntent.getBroadcast(this, 1,
                    deafenIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_channels,
                    getString(R.string.overlay), PendingIntent.getBroadcast(this, 2,
                    overlayIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        }

        Intent channelListIntent = new Intent(this, PlumbleActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, channelListIntent, 0);

        builder.setContentIntent(pendingIntent);

        mStatusNotificationBuilder = builder;
        startForeground(STATUS_NOTIFICATION_ID, builder.build());
    }

    /**
     * Updates the status notification with unread messages (if applicable), or status updates.
     */
    public void updateNotificationState() throws RemoteException {
        if(mStatusNotificationBuilder == null) return;
        String contentText;
        if(isConnected()) {
            User currentUser = mBinder.getSessionUser();
            if(currentUser.isSelfMuted() && currentUser.isSelfDeafened())
                contentText = getString(R.string.status_notify_muted_and_deafened);
            else if(currentUser.isSelfMuted())
                contentText = getString(R.string.status_notify_muted);
            else
                contentText = getString(R.string.connected);
        } else {
            contentText = getString(R.string.disconnected);
        }

        if(getBinder().isReconnecting()) {
            contentText = getString(R.string.reconnecting, PlumbleActivity.RECONNECT_DELAY/1000);
        }

        if(!mUnreadMessages.isEmpty() && isConnected()) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            for(String message : mUnreadMessages)
                inboxStyle.addLine(message);
            mStatusNotificationBuilder.setStyle(inboxStyle);
        } else {
            mStatusNotificationBuilder.setStyle(null);
        }

        mStatusNotificationBuilder.setContentText(contentText);
        startForeground(STATUS_NOTIFICATION_ID, mStatusNotificationBuilder.build());
    }

    public void updateNotificationTicker(String message) {
        if(mStatusNotificationBuilder == null) return;
        mStatusNotificationBuilder.setTicker(message);
        startForeground(STATUS_NOTIFICATION_ID, mStatusNotificationBuilder.build());
    }

    public void hideNotification() {
        stopForeground(true);
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
            if(mUnreadMessages.size() > 0) {
                mUnreadMessages.clear();
                if(isConnected()) updateNotificationState();
            }
        }
    }
}
