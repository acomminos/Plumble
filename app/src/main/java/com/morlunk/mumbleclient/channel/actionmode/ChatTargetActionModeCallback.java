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

package com.morlunk.mumbleclient.channel.actionmode;

import android.support.v7.view.ActionMode;
import android.view.Menu;

import com.morlunk.mumbleclient.channel.ChatTargetProvider;

/**
 * A callback that sets the active chat target when activated, and resets when destroyed (usually
 * to the user's current channel).
 * Created by andrew on 26/06/14.
 */
public abstract class ChatTargetActionModeCallback implements ActionMode.Callback {
    private ChatTargetProvider mProvider;

    public ChatTargetActionModeCallback(ChatTargetProvider provider) {
        mProvider = provider;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mProvider.setChatTarget(getChatTarget());
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mProvider.setChatTarget(null);
    }

    public abstract ChatTargetProvider.ChatTarget getChatTarget();
}
