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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.ChatTargetProvider;
import com.morlunk.mumbleclient.channel.comment.UserCommentFragment;
import com.morlunk.mumbleclient.util.ModelUtils;
import com.morlunk.mumbleclient.util.TintedMenuInflater;

import java.util.List;

/**
 * Contextual action mode for users.
 * When the action mode is activated, the user is set to the current chat target.
 * Upon dismissal, the chat target is reset (usually to the channel).
 * Created by andrew on 24/06/14.
 */
public class UserActionModeCallback extends ChatTargetActionModeCallback {
    private IUser mUser;

    public UserActionModeCallback(IUser user,
                                  ChatTargetProvider chatTargetProvider) {
        super(chatTargetProvider);
        mUser = user;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        super.onCreateActionMode(actionMode, menu);
        actionMode.setTitle(mUser.getName());
        actionMode.setSubtitle(R.string.current_chat_target);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        super.onDestroyActionMode(actionMode);
    }

    @Override
    public ChatTargetProvider.ChatTarget getChatTarget() {
        return new ChatTargetProvider.ChatTarget(mUser);
    }
}
