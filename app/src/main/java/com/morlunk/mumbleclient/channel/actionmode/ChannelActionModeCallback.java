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
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.ChannelEditFragment;
import com.morlunk.mumbleclient.channel.ChatTargetProvider;
import com.morlunk.mumbleclient.channel.comment.ChannelDescriptionFragment;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.util.TintedMenuInflater;

/**
 * Contextual action mode for channels.
 * When the action mode is activated, the user is set to the current chat target.
 * Upon dismissal, the chat target is reset (usually to the current channel).
 * Created by andrew on 24/06/14.
 */
public class ChannelActionModeCallback extends ChatTargetActionModeCallback {
    private Context mContext;
    private IJumbleService mService;
    private Channel mChannel;
    private PlumbleDatabase mDatabase;
    private FragmentManager mFragmentManager;

    public ChannelActionModeCallback(Context context,
                                     IJumbleService service,
                                     Channel channel,
                                     ChatTargetProvider chatTargetProvider,
                                     PlumbleDatabase database,
                                     FragmentManager fragmentManager) {
        super(chatTargetProvider);
        mContext = context;
        mService = service;
        mChannel = channel;
        mDatabase = database;
        mFragmentManager = fragmentManager;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        super.onCreateActionMode(actionMode, menu);
        TintedMenuInflater inflater = new TintedMenuInflater(mContext, actionMode.getMenuInflater());
        inflater.inflate(R.menu.context_channel, menu);

        actionMode.setTitle(mChannel.getName());
        actionMode.setSubtitle(R.string.current_chat_target);

        try {
            // Request permissions update from server, if we don't have channel permissions
            if(mChannel.getPermissions() == 0)
                mService.requestPermissions(mChannel.getId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        int perms = mChannel.getPermissions();

        // This breaks uMurmur ACL. Put in a fix based on server version perhaps?
        //menu.getMenu().findItem(R.id.menu_channel_add)
        // .setVisible((permissions & (Permissions.MakeChannel | Permissions.MakeTempChannel)) > 0);
        menu.findItem(R.id.context_channel_edit).setVisible((perms & Permissions.Write) > 0);
        menu.findItem(R.id.context_channel_remove).setVisible((perms & Permissions.Write) > 0);
        menu.findItem(R.id.context_channel_view_description)
                .setVisible(mChannel.getDescription() != null ||
                        mChannel.getDescriptionHash() != null);

        try {
            Server server = mService.getConnectedServer();
            if(server != null) {
                menu.findItem(R.id.context_channel_pin)
                        .setChecked(mDatabase.isChannelPinned(server.getId(), mChannel.getId()));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        boolean adding = false;
        switch(menuItem.getItemId()) {
            case R.id.context_channel_join:
                try {
                    mService.joinChannel(mChannel.getId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.context_channel_add:
                adding = true;
            case R.id.context_channel_edit:
                ChannelEditFragment addFragment = new ChannelEditFragment();
                Bundle args = new Bundle();
                if(adding) args.putInt("parent", mChannel.getId());
                else args.putInt("channel", mChannel.getId());
                args.putBoolean("adding", adding);
                addFragment.setArguments(args);
                addFragment.show(mFragmentManager, "ChannelAdd");
                break;
            case R.id.context_channel_remove:
                AlertDialog.Builder adb = new AlertDialog.Builder(mContext);
                adb.setTitle(R.string.confirm);
                adb.setMessage(R.string.confirm_delete_channel);
                adb.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mService.removeChannel(mChannel.getId());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
                adb.setNegativeButton(android.R.string.cancel, null);
                adb.show();
                break;
            case R.id.context_channel_view_description:
                Bundle commentArgs = new Bundle();
                commentArgs.putInt("channel", mChannel.getId());
                commentArgs.putString("comment", mChannel.getDescription());
                commentArgs.putBoolean("editing", false);
                DialogFragment commentFragment = (DialogFragment) Fragment.instantiate(mContext,
                        ChannelDescriptionFragment.class.getName(), commentArgs);
                commentFragment.show(mFragmentManager, ChannelDescriptionFragment.class.getName());
                break;
            case R.id.context_channel_pin:
                try {
                    long serverId = mService.getConnectedServer().getId();
                    boolean pinned = mDatabase.isChannelPinned(serverId, mChannel.getId());
                    if(!pinned) mDatabase.addPinnedChannel(serverId, mChannel.getId());
                    else mDatabase.removePinnedChannel(serverId, mChannel.getId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
        actionMode.finish();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        super.onDestroyActionMode(actionMode);
    }

    @Override
    public ChatTargetProvider.ChatTarget getChatTarget() {
        return new ChatTargetProvider.ChatTarget(mChannel);
    }
}
