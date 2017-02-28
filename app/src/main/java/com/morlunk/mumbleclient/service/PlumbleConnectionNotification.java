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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.NotificationCompat;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.DrawerAdapter;
import com.morlunk.mumbleclient.app.PlumbleActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper to create Plumble notifications.
 * Created by andrew on 08/08/14.
 */
public class PlumbleConnectionNotification {
    private static final int NOTIFICATION_ID = 1;
    private static final String BROADCAST_MUTE = "b_mute";
    private static final String BROADCAST_DEAFEN = "b_deafen";
    private static final String BROADCAST_OVERLAY = "b_overlay";

    private Service mService;
    private OnActionListener mListener;
    private String mCustomTicker;
    private String mCustomContentText;
    private boolean mActionsShown;

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_MUTE.equals(intent.getAction())) {
                mListener.onMuteToggled();
            } else if (BROADCAST_DEAFEN.equals(intent.getAction())) {
                mListener.onDeafenToggled();
            } else if (BROADCAST_OVERLAY.equals(intent.getAction())) {
                mListener.onOverlayToggled();
            }
        }
    };

    /**
     * Creates a foreground Plumble notification for the given service.
     * @param service The service to register a foreground notification for.
     * @param listener An listener for notification actions.
     * @return A new PlumbleNotification instance.
     */
    public static PlumbleConnectionNotification create(Service service, String ticker, String contentText,
                                                       OnActionListener listener) {
        return new PlumbleConnectionNotification(service, ticker, contentText, listener);
    }

    private PlumbleConnectionNotification(Service service, String ticker, String contentText,
                                          OnActionListener listener) {
        mService = service;
        mListener = listener;
        mCustomTicker = ticker;
        mCustomContentText = contentText;
        mActionsShown = false;
    }

    public void setCustomTicker(String ticker) {
        mCustomTicker = ticker;
    }

    public void setCustomContentText(String text) {
        mCustomContentText = text;
    }

    public void setActionsShown(boolean actionsShown) {
        mActionsShown = actionsShown;
    }

    /**
     * Shows the notification and registers the notification action button receiver.
     */
    public void show() {
        createNotification();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_DEAFEN);
        filter.addAction(BROADCAST_MUTE);
        filter.addAction(BROADCAST_OVERLAY);
        try {
            mService.registerReceiver(mNotificationReceiver, filter);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is already registered.
            e.printStackTrace();
        }
    }

    /**
     * Hides the notification and unregisters the action receiver.
     */
    public void hide() {
        try {
            mService.unregisterReceiver(mNotificationReceiver);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is not registered.
            e.printStackTrace();
        }
        mService.stopForeground(true);
    }

    /**
     * Called to update/create the service's foreground Plumble notification.
     */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService);
        builder.setContentTitle(mService.getString(R.string.app_name));
        builder.setContentText(mCustomContentText);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setOngoing(true);

        if (mActionsShown) {
            // Add notification triggers
            Intent muteIntent = new Intent(BROADCAST_MUTE);
            Intent deafenIntent = new Intent(BROADCAST_DEAFEN);
            Intent overlayIntent = new Intent(BROADCAST_OVERLAY);

            builder.addAction(R.drawable.ic_action_microphone,
                    mService.getString(R.string.mute), PendingIntent.getBroadcast(mService, 1,
                            muteIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_audio,
                    mService.getString(R.string.deafen), PendingIntent.getBroadcast(mService, 1,
                            deafenIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.addAction(R.drawable.ic_action_channels,
                    mService.getString(R.string.overlay), PendingIntent.getBroadcast(mService, 2,
                            overlayIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        }

        Intent channelListIntent = new Intent(mService, PlumbleActivity.class);
        channelListIntent.putExtra(PlumbleActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER);
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0, channelListIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        Notification notification = builder.build();
        mService.startForeground(NOTIFICATION_ID, notification);
        return notification;
    }

    public interface OnActionListener {
        void onMuteToggled();
        void onDeafenToggled();
        void onOverlayToggled();
    }
}
