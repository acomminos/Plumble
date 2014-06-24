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
import android.view.View;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;

/**
 * Created by andrew on 24/06/14.
 */
public class ChannelActionModeCallback implements ActionMode.Callback {
    private IJumbleService mService;
    private Channel mChannel;

    public ChannelActionModeCallback(IJumbleService service, Channel channel) {
        mService = service;
        mChannel = channel;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        return false;
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

    }
}
