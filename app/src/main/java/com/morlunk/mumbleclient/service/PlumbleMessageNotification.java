/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IMessage;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.DrawerAdapter;
import com.morlunk.mumbleclient.app.PlumbleActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * A notification indicating that new messages have been received.
 * Intended to augment the existing {@link PlumbleConnectionNotification} by providing a higher
 * priority heads-up display on Android 5.0+ devices, as well as vibration.
 * Created by andrew on 25/03/16.
 */
public class PlumbleMessageNotification {
    private static final int NOTIFICATION_ID = 2;
    private static final long VIBRATION_PATTERN[] = { 0, 100 };

    private final Context mContext;
    private final List<IMessage> mUnreadMessages;

    public PlumbleMessageNotification(Context context) {
        mContext = context;
        mUnreadMessages = new ArrayList<>();
    }

    /**
     * Shows the notification with the provided message.
     * If the notification is already shown, append the message to the existing notification.
     * @param message The message to notify the user about.
     */
    public void show(IMessage message) {
        mUnreadMessages.add(message);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(mContext.getString(R.string.notification_unread_many, mUnreadMessages.size()));
        for (IMessage m : mUnreadMessages) {
            String line = mContext.getString(R.string.notification_message, m.getActorName(), m.getMessage());
            style.addLine(line);
        }

        Intent channelListIntent = new Intent(mContext, PlumbleActivity.class);
        channelListIntent.putExtra(PlumbleActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER);
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, channelListIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTicker(message.getActorName())
                .setContentTitle(message.getActorName())
                .setContentText(message.getMessage())
                .setVibrate(VIBRATION_PATTERN)
                .setStyle(style);

        if (mUnreadMessages.size() > 0)
            builder.setNumber(mUnreadMessages.size());

        final NotificationManagerCompat manager = NotificationManagerCompat.from(mContext);
        Notification notification = builder.build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Dismisses the unread messages notification, marking all messages read.
     */
    public void dismiss() {
        mUnreadMessages.clear();
        final NotificationManagerCompat manager = NotificationManagerCompat.from(mContext);
        manager.cancel(NOTIFICATION_ID);
    }
}
