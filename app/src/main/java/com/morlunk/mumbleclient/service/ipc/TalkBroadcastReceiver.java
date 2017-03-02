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

package com.morlunk.mumbleclient.service.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.IJumbleSession;

/**
 * Created by andrew on 08/08/14.
 */
public class TalkBroadcastReceiver extends BroadcastReceiver {
    public static final String BROADCAST_TALK = "com.morlunk.mumbleclient.action.TALK";
    public static final String EXTRA_TALK_STATUS = "status";
    public static final String TALK_STATUS_ON = "on";
    public static final String TALK_STATUS_OFF = "off";
    public static final String TALK_STATUS_TOGGLE = "toggle";

    private IJumbleService mService;

    public TalkBroadcastReceiver(IJumbleService service) {
        mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BROADCAST_TALK.equals(intent.getAction())) {
            if (!mService.isConnected())
                return;
            IJumbleSession session = mService.getSession();
            String status = intent.getStringExtra(EXTRA_TALK_STATUS);
            if (status == null) status = TALK_STATUS_TOGGLE;
            if (TALK_STATUS_ON.equals(status)) {
                session.setTalkingState(true);
            } else if (TALK_STATUS_OFF.equals(status)) {
                session.setTalkingState(false);
            } else if (TALK_STATUS_TOGGLE.equals(status)) {
                session.setTalkingState(!session.isTalking());
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
