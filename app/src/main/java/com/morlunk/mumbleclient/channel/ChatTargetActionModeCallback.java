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

package com.morlunk.mumbleclient.channel;

import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.ChatTargetProvider;

/**
 * A callback that sets the active chat target when activated, and resets when destroyed (usually
 * to the user's current channel).
 * Created by andrew on 26/06/14.
 */
public class ChatTargetActionModeCallback implements ActionMode.Callback {
    private final ChatTargetProvider mProvider;
    private final ChatTargetProvider.ChatTarget mChatTarget;

    public ChatTargetActionModeCallback(ChatTargetProvider provider, ChatTargetProvider.ChatTarget target) {
        mProvider = provider;
        mChatTarget = target;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.setTitle(mChatTarget.getName());
        actionMode.setSubtitle(R.string.current_chat_target);
        mProvider.setChatTarget(getChatTarget());
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mProvider.setChatTarget(null);
    }

    public ChatTargetProvider.ChatTarget getChatTarget() {
        return mChatTarget;
    }
}
