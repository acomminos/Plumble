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

package com.morlunk.mumbleclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.morlunk.jumble.Constants;
import com.morlunk.mumbleclient.db.DatabaseCertificate;
import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;

/**
 * Singleton settings class for universal access to the app's preferences.
 * @author morlunk
 */
public class Settings {
    public static final String PREF_INPUT_METHOD = "audioInputMethod";
    public static final Set<String> ARRAY_INPUT_METHODS;
    /** Voice activity transmits depending on the amplitude of user input. */
    public static final String ARRAY_INPUT_METHOD_VOICE = "voiceActivity";
    /** Push to talk transmits on command. */
    public static final String ARRAY_INPUT_METHOD_PTT = "ptt";
    /** Continuous transmits always. */
    public static final String ARRAY_INPUT_METHOD_CONTINUOUS = "continuous";

    public static final String PREF_THRESHOLD = "vadThreshold";
    public static final int DEFAULT_THRESHOLD = 50;

    public static final String PREF_PUSH_KEY = "talkKey";
    public static final Integer DEFAULT_PUSH_KEY = -1;

    public static final String PREF_HOT_CORNER_KEY = "hotCorner";
    public static final String ARRAY_HOT_CORNER_NONE = "none";
    public static final String ARRAY_HOT_CORNER_TOP_LEFT = "topLeft";
    public static final String ARRAY_HOT_CORNER_BOTTOM_LEFT = "bottomLeft";
    public static final String ARRAY_HOT_CORNER_TOP_RIGHT = "topRight";
    public static final String ARRAY_HOT_CORNER_BOTTOM_RIGHT = "bottomRight";
    public static final String DEFAULT_HOT_CORNER = ARRAY_HOT_CORNER_NONE;

    public static final String PREF_PUSH_BUTTON_HIDE_KEY = "hidePtt";
    public static final Boolean DEFAULT_PUSH_BUTTON_HIDE = false;

    public static final String PREF_PTT_TOGGLE = "togglePtt";
    public static final Boolean DEFAULT_PTT_TOGGLE = false;

    public static final String PREF_INPUT_RATE = "input_quality";
    public static final String DEFAULT_RATE = "48000";

    public static final String PREF_INPUT_QUALITY = "input_bitrate";
    public static final int DEFAULT_INPUT_QUALITY = 40000;

    public static final String PREF_AMPLITUDE_BOOST = "inputVolume";
    public static final Integer DEFAULT_AMPLITUDE_BOOST = 100;

    public static final String PREF_CHAT_NOTIFY = "chatNotify";
    public static final Boolean DEFAULT_CHAT_NOTIFY = true;

    public static final String PREF_USE_TTS = "useTts";
    public static final Boolean DEFAULT_USE_TTS = true;

    public static final String PREF_SHORT_TTS_MESSAGES = "shortTtsMessages";
    public static final boolean DEFAULT_SHORT_TTS_MESSAGES = false;

    public static final String PREF_AUTO_RECONNECT = "autoReconnect";
    public static final Boolean DEFAULT_AUTO_RECONNECT = true;

    public static final String PREF_THEME = "theme";
    public static final String ARRAY_THEME_LIGHT = "lightDark";
    public static final String ARRAY_THEME_DARK = "dark";
    public static final String ARRAY_THEME_SOLARIZED_LIGHT = "solarizedLight";
    public static final String ARRAY_THEME_SOLARIZED_DARK = "solarizedDark";

    public static final String PREF_PTT_BUTTON_HEIGHT = "pttButtonHeight";
    public static final int DEFAULT_PTT_BUTTON_HEIGHT = 150;

    /** @deprecated use {@link #PREF_CERT_ID } */
    public static final String PREF_CERT_DEPRECATED = "certificatePath";
    /** @deprecated use {@link #PREF_CERT_ID } */
    public static final String PREF_CERT_PASSWORD_DEPRECATED = "certificatePassword";

    /**
     * The DB identifier for the default certificate.
     * @see com.morlunk.mumbleclient.db.DatabaseCertificate
     */
    public static final String PREF_CERT_ID = "certificateId";

    public static final String PREF_DEFAULT_USERNAME = "defaultUsername";
    public static final String DEFAULT_DEFAULT_USERNAME = "Plumble_User"; // funny var name

    public static final String PREF_FORCE_TCP = "forceTcp";
    public static final Boolean DEFAULT_FORCE_TCP = false;

    public static final String PREF_USE_TOR = "useTor";
    public static final Boolean DEFAULT_USE_TOR = false;

    public static final String PREF_DISABLE_OPUS = "disableOpus";
    public static final Boolean DEFAULT_DISABLE_OPUS = false;

    public static final String PREF_MUTED = "muted";
    public static final Boolean DEFAULT_MUTED = false;

    public static final String PREF_DEAFENED = "deafened";
    public static final Boolean DEFAULT_DEAFENED = false;

    public static final String PREF_FIRST_RUN = "firstRun";
    public static final Boolean DEFAULT_FIRST_RUN = true;

    public static final String PREF_LOAD_IMAGES = "load_images";
    public static final boolean DEFAULT_LOAD_IMAGES = true;

    public static final String PREF_FRAMES_PER_PACKET = "audio_per_packet";
    public static final String DEFAULT_FRAMES_PER_PACKET = "2";

    public static final String PREF_HALF_DUPLEX = "half_duplex";
    public static final boolean DEFAULT_HALF_DUPLEX = false;

    public static final String PREF_HANDSET_MODE = "handset_mode";
    public static final boolean DEFAULT_HANDSET_MODE = false;

    public static final String PREF_PTT_SOUND = "ptt_sound";
    public static final boolean DEFAULT_PTT_SOUND = false;

    public static final String PREF_PREPROCESSOR_ENABLED = "preprocessor_enabled";
    public static final boolean DEFAULT_PREPROCESSOR_ENABLED = true;

    public static final String PREF_STAY_AWAKE = "stay_awake";
    public static final boolean DEFAULT_STAY_AWAKE = false;

    public static final String PREF_SHOW_USER_COUNT = "show_user_count";
    public static final boolean DEFAULT_SHOW_USER_COUNT = false;

    public static final String PREF_START_UP_IN_PINNED_MODE = "startUpInPinnedMode";
    public static final boolean DEFAULT_START_UP_IN_PINNED_MODE = false;

    static {
        ARRAY_INPUT_METHODS = new HashSet<String>();
        ARRAY_INPUT_METHODS.add(ARRAY_INPUT_METHOD_VOICE);
        ARRAY_INPUT_METHODS.add(ARRAY_INPUT_METHOD_PTT);
        ARRAY_INPUT_METHODS.add(ARRAY_INPUT_METHOD_CONTINUOUS);
    }

    private final SharedPreferences preferences;

    public static Settings getInstance(Context context) {
        return new Settings(context);
    }

    private Settings(Context ctx) {
        preferences = PreferenceManager.getDefaultSharedPreferences(ctx);

        // TODO(acomminos): Settings migration infra.
        if (preferences.contains(PREF_CERT_DEPRECATED)) {
            // Perform legacy certificate migration into PlumbleSQLiteDatabase.
            Toast.makeText(ctx, R.string.migration_certificate_begin, Toast.LENGTH_LONG).show();
            String certPath = preferences.getString(PREF_CERT_DEPRECATED, "");
            String certPassword = preferences.getString(PREF_CERT_PASSWORD_DEPRECATED, "");

            Log.d(com.morlunk.mumbleclient.Constants.TAG, "Migrating certificate from " + certPath);
            try {
                File certFile = new File(certPath);
                FileInputStream certInput = new FileInputStream(certFile);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                KeyStore oldStore = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
                oldStore.load(certInput, certPassword.toCharArray());
                oldStore.store(outputStream, new char[0]);

                PlumbleSQLiteDatabase database = new PlumbleSQLiteDatabase(ctx);
                DatabaseCertificate certificate =
                        database.addCertificate(certFile.getName(), outputStream.toByteArray());
                database.close();

                setDefaultCertificateId(certificate.getId());

                Toast.makeText(ctx, R.string.migration_certificate_success, Toast.LENGTH_LONG).show();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                // We can safely ignore this; the only case in which we might still want to recover
                // would be if the user's external storage is removed.
            } catch (CertificateException e) {
                // Likely caused due to stored password being incorrect.
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                preferences.edit()
                        .remove(PREF_CERT_DEPRECATED)
                        .remove(PREF_CERT_PASSWORD_DEPRECATED)
                        .apply();
            }

        }
    }

    public String getInputMethod() {
        String method = preferences.getString(PREF_INPUT_METHOD, ARRAY_INPUT_METHOD_VOICE);
        if(!ARRAY_INPUT_METHODS.contains(method)) {
            // Set default method for users who used to use handset mode before removal.
            method = ARRAY_INPUT_METHOD_VOICE;
        }
        return method;
    }

    /**
     * Converts the preference input method value to the one used to connect to a server via Jumble.
     * @return An input method value used to instantiate a Jumble service.
     */
    public int getJumbleInputMethod() {
        String inputMethod = getInputMethod();
        if (ARRAY_INPUT_METHOD_VOICE.equals(inputMethod)) {
            return Constants.TRANSMIT_VOICE_ACTIVITY;
        } else if (ARRAY_INPUT_METHOD_PTT.equals(inputMethod)) {
            return Constants.TRANSMIT_PUSH_TO_TALK;
        } else if (ARRAY_INPUT_METHOD_CONTINUOUS.equals(inputMethod)) {
            return Constants.TRANSMIT_CONTINUOUS;
        }
        throw new RuntimeException("Could not convert input method '" + inputMethod + "' to a Jumble input method id!");
    }

    public void setInputMethod(String inputMethod) {
        if(ARRAY_INPUT_METHOD_VOICE.equals(inputMethod) ||
                ARRAY_INPUT_METHOD_PTT.equals(inputMethod) ||
                ARRAY_INPUT_METHOD_CONTINUOUS.equals(inputMethod)) {
            preferences.edit().putString(PREF_INPUT_METHOD, inputMethod).apply();
        } else {
            throw new RuntimeException("Invalid input method " + inputMethod);
        }
    }

    public int getInputSampleRate() {
        return Integer.parseInt(preferences.getString(Settings.PREF_INPUT_RATE, DEFAULT_RATE));
    }

    public int getInputQuality() {
        return preferences.getInt(Settings.PREF_INPUT_QUALITY, DEFAULT_INPUT_QUALITY);
    }

    public float getAmplitudeBoostMultiplier() {
        return (float)preferences.getInt(Settings.PREF_AMPLITUDE_BOOST, DEFAULT_AMPLITUDE_BOOST)/100;
    }

    public float getDetectionThreshold() {
        return (float)preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD)/100;
    }

    public int getPushToTalkKey() {
        return preferences.getInt(PREF_PUSH_KEY, DEFAULT_PUSH_KEY);
    }

    public String getHotCorner() {
        return preferences.getString(PREF_HOT_CORNER_KEY, DEFAULT_HOT_CORNER);
    }

    /**
     * Returns whether or not the hot corner is enabled.
     * @return true if a hot corner should be shown.
     */
    public boolean isHotCornerEnabled() {
        return !ARRAY_HOT_CORNER_NONE.equals(preferences.getString(PREF_HOT_CORNER_KEY, DEFAULT_HOT_CORNER));
    }

    /**
     * Returns the view gravity of the hot corner, or 0 if hot corner is disabled.
     * @return A {@link android.view.Gravity} value, or 0 if disabled.
     */
    public int getHotCornerGravity() {
        String hc = getHotCorner();
        if(ARRAY_HOT_CORNER_BOTTOM_LEFT.equals(hc)) {
            return Gravity.LEFT | Gravity.BOTTOM;
        } else if(ARRAY_HOT_CORNER_BOTTOM_RIGHT.equals(hc)) {
            return Gravity.RIGHT | Gravity.BOTTOM;
        } else if(ARRAY_HOT_CORNER_TOP_LEFT.equals(hc)) {
            return Gravity.LEFT | Gravity.TOP;
        } else if(ARRAY_HOT_CORNER_TOP_RIGHT.equals(hc)) {
            return Gravity.RIGHT | Gravity.TOP;
        }
        return 0;
    }

    /**
     * @return the resource ID of the user-defined theme.
     */
    public int getTheme() {
        String theme = preferences.getString(PREF_THEME, ARRAY_THEME_LIGHT);
        if(ARRAY_THEME_LIGHT.equals(theme))
            return R.style.Theme_Plumble;
        else if(ARRAY_THEME_DARK.equals(theme))
            return R.style.Theme_Plumble_Dark;
        else if(ARRAY_THEME_SOLARIZED_LIGHT.equals(theme))
            return R.style.Theme_Plumble_Solarized_Light;
        else if(ARRAY_THEME_SOLARIZED_DARK.equals(theme))
            return R.style.Theme_Plumble_Solarized_Dark;
        return -1;
    }

    /* @return the height of PTT button */
    public int getPTTButtonHeight() {
        return preferences.getInt(Settings.PREF_PTT_BUTTON_HEIGHT, DEFAULT_PTT_BUTTON_HEIGHT);
    }

    /**
     * Returns a database identifier for the default certificate, or a negative number if there is
     * no default certificate set.
     * @return The default certificate's ID, or a negative integer if not set.
     */
    public long getDefaultCertificate() {
        return preferences.getLong(PREF_CERT_ID, -1);
    }

    public String getDefaultUsername() {
        return preferences.getString(PREF_DEFAULT_USERNAME, DEFAULT_DEFAULT_USERNAME);
    }

    public boolean isPushToTalkToggle() {
        return preferences.getBoolean(PREF_PTT_TOGGLE, DEFAULT_PTT_TOGGLE);
    }

    public boolean isPushToTalkButtonShown() {
        return !preferences.getBoolean(PREF_PUSH_BUTTON_HIDE_KEY, DEFAULT_PUSH_BUTTON_HIDE);
    }

    public boolean isChatNotifyEnabled() {
        return preferences.getBoolean(PREF_CHAT_NOTIFY, DEFAULT_CHAT_NOTIFY);
    }

    public boolean isTextToSpeechEnabled() {
        return preferences.getBoolean(PREF_USE_TTS, DEFAULT_USE_TTS);
    }

    public boolean isShortTextToSpeechMessagesEnabled() {
        return preferences.getBoolean(PREF_SHORT_TTS_MESSAGES, DEFAULT_SHORT_TTS_MESSAGES);
    }

    public boolean isAutoReconnectEnabled() {
        return preferences.getBoolean(PREF_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT);
    }

    public boolean isTcpForced() {
        return preferences.getBoolean(PREF_FORCE_TCP, DEFAULT_FORCE_TCP);
    }

    public boolean isOpusDisabled() {
        return preferences.getBoolean(PREF_DISABLE_OPUS, DEFAULT_DISABLE_OPUS);
    }

    public boolean isTorEnabled() {
        return preferences.getBoolean(PREF_USE_TOR, DEFAULT_USE_TOR);
    }

    public boolean isMuted() {
        return preferences.getBoolean(PREF_MUTED, DEFAULT_MUTED);
    }

    public boolean isDeafened() {
        return preferences.getBoolean(PREF_DEAFENED, DEFAULT_DEAFENED);
    }

    public boolean isFirstRun() {
        return preferences.getBoolean(PREF_FIRST_RUN, DEFAULT_FIRST_RUN);
    }

    public boolean shouldLoadExternalImages() {
        return preferences.getBoolean(PREF_LOAD_IMAGES, DEFAULT_LOAD_IMAGES);
    }

    public void setMutedAndDeafened(boolean muted, boolean deafened) {
        Editor editor = preferences.edit();
        editor.putBoolean(PREF_MUTED, muted || deafened);
        editor.putBoolean(PREF_DEAFENED, deafened);
        editor.apply();
    }

    public void setFirstRun(boolean run) {
        preferences.edit().putBoolean(PREF_FIRST_RUN, run).apply();
    }

    public int getFramesPerPacket() {
        return Integer.parseInt(preferences.getString(PREF_FRAMES_PER_PACKET, DEFAULT_FRAMES_PER_PACKET));
    }

    public boolean isHalfDuplex() {
        return preferences.getBoolean(PREF_HALF_DUPLEX, DEFAULT_HALF_DUPLEX);
    }

    public boolean isHandsetMode() {
        return preferences.getBoolean(PREF_HANDSET_MODE, DEFAULT_HANDSET_MODE);
    }

    public boolean isPttSoundEnabled() {
        return preferences.getBoolean(PREF_PTT_SOUND, DEFAULT_PTT_SOUND);
    }

    public boolean isPreprocessorEnabled() {
        return preferences.getBoolean(PREF_PREPROCESSOR_ENABLED, DEFAULT_PREPROCESSOR_ENABLED);
    }

    public boolean shouldStayAwake() {
        return preferences.getBoolean(PREF_STAY_AWAKE, DEFAULT_STAY_AWAKE);
    }

    public void setDefaultCertificateId(long defaultCertificateId) {
        preferences.edit().putLong(PREF_CERT_ID, defaultCertificateId).apply();
    }

    public void disableCertificate() {
        preferences.edit().putLong(PREF_CERT_ID, -1).apply();
    }

    public boolean isUsingCertificate() {
        return getDefaultCertificate() >= 0;
    }

    /**
     * @return true if the user count should be shown next to channels.
     */
    public boolean shouldShowUserCount() {
        return preferences.getBoolean(PREF_SHOW_USER_COUNT, DEFAULT_SHOW_USER_COUNT);
    }

    public boolean shouldStartUpInPinnedMode() {
        return preferences.getBoolean(PREF_START_UP_IN_PINNED_MODE, DEFAULT_START_UP_IN_PINNED_MODE);
    }
}
