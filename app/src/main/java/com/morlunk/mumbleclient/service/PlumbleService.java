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
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleObserver;
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

    /** Undocumented constant that permits a proximity-sensing wake lock. */
    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
    public static final int TTS_THRESHOLD = 250; // Maximum number of characters to read

    public static final String BROADCAST_MUTE = "broadcast_mute";
    public static final String BROADCAST_DEAFEN = "broadcast_deafen";
    public static final String BROADCAST_TOGGLE_OVERLAY = "broadcast_toggle_overlay";

    public static final int STATUS_NOTIFICATION_ID = 1;

    private NotificationCompat.Builder mStatusNotificationBuilder;
    /** A list of messages to be displayed in the chat notification. Should be cleared when notification pressed. */
    private List<String> mUnreadMessages = new ArrayList<String>();

    private Settings mSettings;
    private boolean mHotCornerEnabled;
    /** The view representing the hot corner. */
    private View mHotCornerView;
    /** The overlay that's shown when talking using a hot corner. */
    private View mHotCornerOverlay;
    /** Channel view overlay. */
    private PlumbleOverlay mChannelOverlay;
    /** Proximity lock for handset mode. */
    private PowerManager.WakeLock mProximityLock;

    private TextToSpeech mTTS;
    private TextToSpeech.OnInitListener mTTSInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if(status == TextToSpeech.ERROR)
                logWarning(getString(R.string.tts_failed));
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
                } else if(PlumbleActivity.ACTION_PLUMBLE_SHOWN.equals(intent.getAction())) {
                    if(getBinder().isConnected()) {
                        mUnreadMessages.clear();
                        updateNotificationState();
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private JumbleObserver mObserver = new JumbleObserver() {

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            String tickerMessage = null;
            if(reconnecting) tickerMessage += "\n"+getString(R.string.reconnecting, PlumbleActivity.RECONNECT_DELAY/1000);
            else tickerMessage = getString(R.string.plumbleDisconnected);
            if(mNotificationReceiver == null) createNotification();
            updateNotificationTicker(tickerMessage);
            updateNotificationState();
            if(!reconnecting) hideNotification();
        }

        @Override
        public void onUserStateUpdated(User user) throws RemoteException {
            if(user.getSession() == getSession()) {
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
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Register for notification actions
        IntentFilter notificationIntentFilter = new IntentFilter();
        notificationIntentFilter.addAction(BROADCAST_MUTE);
        notificationIntentFilter.addAction(BROADCAST_DEAFEN);
        notificationIntentFilter.addAction(BROADCAST_TOGGLE_OVERLAY);
        notificationIntentFilter.addAction(PlumbleActivity.ACTION_PLUMBLE_SHOWN);
        registerReceiver(mNotificationReceiver, notificationIntentFilter);

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
        createNotification();
        try {
            // Restore mute/deafen state
            Settings settings = Settings.getInstance(this);
            User sessionUser = getBinder().getSessionUser();
            if(sessionUser.isSelfMuted() != settings.isMuted() ||
                    sessionUser.isSelfDeafened() != settings.isDeafened())
                getBinder().setSelfMuteDeafState(settings.isMuted(), settings.isDeafened());

            // Update setting-dependent connection properties
            configureHotCorner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Configure proximity sensor
        if(Settings.ARRAY_INPUT_METHOD_HANDSET.equals(mSettings.getInputMethod())) {
            setProximitySensorOn(true);
        }
    }

    @Override
    public void onConnectionDisconnected() {
        super.onConnectionDisconnected();
        // Remove overlay if present.
        mChannelOverlay.hide();

        // Remove hot corner if present.
        if(mHotCornerView != null) {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            try {
                windowManager.removeView(mHotCornerView);
                windowManager.removeView(mHotCornerOverlay);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            mHotCornerEnabled = false;
        }

        setProximitySensorOn(false);

        try {
            if(!getBinder().isReconnecting()) hideNotification();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        stopSelf(); // Stop manual control of the service's lifecycle.
    }

    /**
     * Sets up or updates the hot corner with the latest values from settings.
     */
    public void configureHotCorner() {
        // Hot corner configuration
        String hotCorner = mSettings.getHotCorner();
        final WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if(!Settings.ARRAY_HOT_CORNER_NONE.equals(hotCorner) && isConnected()) {
            final WindowManager.LayoutParams cornerParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);

            if(Settings.ARRAY_HOT_CORNER_TOP_LEFT.equals(hotCorner))
                cornerParams.gravity = Gravity.TOP | Gravity.LEFT;
            else if(Settings.ARRAY_HOT_CORNER_TOP_RIGHT.equals(hotCorner))
                cornerParams.gravity = Gravity.TOP | Gravity.RIGHT;
            else if(Settings.ARRAY_HOT_CORNER_BOTTOM_LEFT.equals(hotCorner))
                cornerParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            else if(Settings.ARRAY_HOT_CORNER_BOTTOM_RIGHT.equals(hotCorner))
                cornerParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;

            final WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            overlayParams.gravity = Gravity.CENTER | Gravity.BOTTOM;

            if(mHotCornerEnabled) {
                windowManager.updateViewLayout(mHotCornerView, cornerParams);
                windowManager.updateViewLayout(mHotCornerOverlay, overlayParams);
            } else {
                mHotCornerView = View.inflate(this, R.layout.ptt_corner, null);
                mHotCornerOverlay = View.inflate(this, R.layout.ptt_overlay, null);
                mHotCornerOverlay.setVisibility(View.GONE);

                mHotCornerView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        try {
                            if(MotionEvent.ACTION_DOWN == event.getAction()) {
                                mHotCornerOverlay.setVisibility(View.VISIBLE);
                                if(mSettings.isPushToTalkToggle()) {
                                    boolean newState = !getBinder().isTalking();
                                    getBinder().setTalkingState(newState);
                                    mHotCornerOverlay.setVisibility(newState ? View.VISIBLE : View.GONE);
                                } else {
                                    getBinder().setTalkingState(true);
                                    mHotCornerOverlay.setVisibility(View.VISIBLE);
                                }
                                return true;
                            } else if(MotionEvent.ACTION_UP == event.getAction()) {
                                if(!mSettings.isPushToTalkToggle()) {
                                    mHotCornerOverlay.setVisibility(View.GONE);
                                    getBinder().setTalkingState(false);
                                }
                                return true;
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        return false;
                    }
                });

                windowManager.addView(mHotCornerView, cornerParams);
                windowManager.addView(mHotCornerOverlay, overlayParams);

                mHotCornerEnabled = true;
            }
        } else if(mHotCornerEnabled) {
            windowManager.removeView(mHotCornerView);
            windowManager.removeView(mHotCornerOverlay);
            mHotCornerEnabled = false;
        }
    }

    /**
     * Called when the user makes a change to their preferences. Should update all preferences relevant to the service.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(!isConnected()) return; // These properties should all be set on connect regardless.

        if(Settings.PREF_INPUT_METHOD.equals(key)) {
            /* Convert input method defined in settings to an integer format used by Jumble. */
            int inputMethod = 0;
            String prefInputMethod = mSettings.getInputMethod();
            if(Settings.ARRAY_INPUT_METHOD_VOICE.equals(prefInputMethod))
                inputMethod = Constants.TRANSMIT_VOICE_ACTIVITY;
            if(Settings.ARRAY_INPUT_METHOD_PTT.equals(prefInputMethod))
                inputMethod = Constants.TRANSMIT_PUSH_TO_TALK;
            if(Settings.ARRAY_INPUT_METHOD_CONTINUOUS.equals(prefInputMethod) ||
                    Settings.ARRAY_INPUT_METHOD_HANDSET.equals(prefInputMethod))
                inputMethod = Constants.TRANSMIT_CONTINUOUS;

            setProximitySensorOn(Settings.ARRAY_INPUT_METHOD_HANDSET.equals(prefInputMethod));

            try {
                getBinder().setTransmitMode(inputMethod);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mChannelOverlay.setPushToTalkShown(inputMethod == Constants.TRANSMIT_PUSH_TO_TALK);
        }
        else if(Settings.PREF_THRESHOLD.equals(key))
            try {
                getBinder().setVADThreshold(mSettings.getDetectionThreshold());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        else if(Settings.PREF_HOT_CORNER_KEY.equals(key))
            configureHotCorner();
        else if(Settings.PREF_USE_TTS.equals(key)) {
            if(mTTS == null && mSettings.isTextToSpeechEnabled())
                mTTS = new TextToSpeech(this, mTTSInitListener);
            else if(mTTS != null && !mSettings.isTextToSpeechEnabled()) {
                mTTS.shutdown();
                mTTS = null;
            }
        }
        else if(Settings.PREF_AMPLITUDE_BOOST.equals(key))
            try {
                getBinder().setAmplitudeBoost(mSettings.getAmplitudeBoostMultiplier());
            } catch (RemoteException e) {
                e.printStackTrace();
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
            builder.addAction(R.drawable.ic_action_audio_on,
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
    public void updateNotificationState() {
        if(mStatusNotificationBuilder == null) return;
        String contentText;
        if(isConnected()) {
            User currentUser = getUserHandler().getUser(getSession());
            if(currentUser.isSelfMuted() && currentUser.isSelfDeafened())
                contentText = getString(R.string.status_notify_muted_and_deafened);
            else if(currentUser.isSelfMuted())
                contentText = getString(R.string.status_notify_muted);
            else
                contentText = getString(R.string.connected);
        } else {
            contentText = getString(R.string.disconnected);
        }

        try {
            if(getBinder().isReconnecting()) contentText = getString(R.string.reconnecting, PlumbleActivity.RECONNECT_DELAY/1000);
        } catch (RemoteException e) {
            e.printStackTrace();
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
}
