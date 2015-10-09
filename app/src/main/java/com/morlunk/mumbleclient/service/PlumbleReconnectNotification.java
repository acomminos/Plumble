/*
 * Copyright (C) 2015 Andrew Comminos
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.morlunk.mumbleclient.R;

/**
 * A notification indicating auto-reconnect is in progress, or if auto-reconnect is disabled,
 * a prompt to reconnect with the error message.
 * Created by andrew on 17/01/15.
 */
public class PlumbleReconnectNotification {
    private static final int NOTIFICATION_ID = 2;
    private static final String BROADCAST_DISMISS = "b_dismiss";
    private static final String BROADCAST_RECONNECT = "b_reconnect";
    private static final String BROADCAST_CANCEL_RECONNECT = "b_cancel_reconnect";

    private Context mContext;
    private OnActionListener mListener;

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_DISMISS.equals(intent.getAction())) {
                mListener.onReconnectNotificationDismissed();
            } else if (BROADCAST_RECONNECT.equals(intent.getAction())) {
                mListener.reconnect();
            } else if (BROADCAST_CANCEL_RECONNECT.equals(intent.getAction())) {
                mListener.cancelReconnect();
            }
        }
    };

    public static PlumbleReconnectNotification show(Context context,
                                                    String error,
                                                    boolean autoReconnect,
                                                    OnActionListener listener) {
        PlumbleReconnectNotification notification = new PlumbleReconnectNotification(context, listener);
        notification.show(error, autoReconnect);
        return notification;
    }

    public PlumbleReconnectNotification(Context context, OnActionListener listener) {
        mContext = context;
        mListener = listener;
    }

    public void show(String error, boolean autoReconnect) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_DISMISS);
        filter.addAction(BROADCAST_RECONNECT);
        filter.addAction(BROADCAST_CANCEL_RECONNECT);
        try {
            mContext.registerReceiver(mNotificationReceiver, filter);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is already registered.
            e.printStackTrace();
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);
        builder.setContentTitle(mContext.getString(R.string.plumbleDisconnected));
        builder.setContentText(error);
        builder.setTicker(mContext.getString(R.string.plumbleDisconnected));

        Intent dismissIntent = new Intent(BROADCAST_DISMISS);
        builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 2, dismissIntent,
                PendingIntent.FLAG_CANCEL_CURRENT));

        if (autoReconnect) {
            Intent cancelIntent = new Intent(BROADCAST_CANCEL_RECONNECT);
            builder.addAction(R.drawable.ic_action_delete_dark,
                    mContext.getString(R.string.cancel_reconnect),
                    PendingIntent.getBroadcast(mContext, 2,
                            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT));
            builder.setOngoing(true);
        } else {
            Intent reconnectIntent = new Intent(BROADCAST_RECONNECT);
            builder.addAction(R.drawable.ic_action_move,
                    mContext.getString(R.string.reconnect),
                    PendingIntent.getBroadcast(mContext, 2,
                            reconnectIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        }

        NotificationManagerCompat nmc = NotificationManagerCompat.from(mContext);
        nmc.notify(NOTIFICATION_ID, builder.build());
    }

    public void hide() {
        try {
            mContext.unregisterReceiver(mNotificationReceiver);
        } catch (IllegalArgumentException e) {
            // Thrown if receiver is not registered.
            e.printStackTrace();
        }
        NotificationManagerCompat nmc = NotificationManagerCompat.from(mContext);
        nmc.cancel(NOTIFICATION_ID);
    }

    public interface OnActionListener {
        public void onReconnectNotificationDismissed();
        public void reconnect();
        public void cancelReconnect();
    }
}
