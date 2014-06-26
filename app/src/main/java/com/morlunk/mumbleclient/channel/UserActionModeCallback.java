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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.comment.UserCommentFragment;

import java.util.List;

/**
 * Created by andrew on 24/06/14.
 */
public class UserActionModeCallback implements ActionMode.Callback {
    private Context mContext;
    private IJumbleService mService;
    private User mUser;
    private ChatTargetProvider mChatTargetProvider;
    private FragmentManager mFragmentManager;

    public UserActionModeCallback(Context context,
                                  IJumbleService service,
                                  User user,
                                  ChatTargetProvider chatTargetProvider,
                                  FragmentManager fragmentManager) {
        mContext = context;
        mService = service;
        mUser = user;
        mChatTargetProvider = chatTargetProvider;
        mFragmentManager = fragmentManager;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.setTitle(mUser.getName());
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.context_user, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        try {
            // Use permission data to determine the actions available.
            boolean self = mUser.getSession() == mService.getSession();
            int perms = mService.getPermissions();
            Channel channel = mService.getChannel(mUser.getChannelId());
            int channelPerms = channel.getId() != 0 ? channel.getPermissions() : perms;

            menu.findItem(R.id.context_kick).setVisible(
                    !self && (perms & (Permissions.Kick | Permissions.Ban | Permissions.Write)) > 0);
            menu.findItem(R.id.context_ban).setVisible(
                    !self && (perms & (Permissions.Ban | Permissions.Write)) > 0);
            menu.findItem(R.id.context_mute).setVisible(
                    ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0 &&
                            (!self || mUser.isMuted() || mUser.isSuppressed())));
            menu.findItem(R.id.context_deafen).setVisible(
                    ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0 &&
                            (!self || mUser.isDeafened())));
            menu.findItem(R.id.context_priority).setVisible(
                    ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0));
            menu.findItem(R.id.context_move).setVisible(
                    !self && (perms & Permissions.Move) > 0);
            menu.findItem(R.id.context_change_comment).setVisible(self);
            menu.findItem(R.id.context_reset_comment).setVisible(
                    !self && mUser.getCommentHash() != null &&
                            !mUser.getCommentHash().isEmpty() &&
                            (perms & (Permissions.Move | Permissions.Write)) > 0);
            menu.findItem(R.id.context_view_comment).setVisible(
                    (mUser.getComment() != null && !mUser.getComment().isEmpty()) ||
                            (mUser.getCommentHash() != null && !mUser.getCommentHash().isEmpty()));
            menu.findItem(R.id.context_register).setVisible(mUser.getUserId() < 0 &&
                    (mUser.getHash() != null && !mUser.getHash().isEmpty()) &&
                    (perms & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0);
            menu.findItem(R.id.context_local_mute).setVisible(!self);
            menu.findItem(R.id.context_ignore_messages).setVisible(!self);

            // TODO info
//            informationItem.enabled = (((perms & (Permissions.Write | Permissions.Register))) > 0 || (channelPermissions & (Permissions.Write | Permissions.Enter)) > 0 || (mUser.getSession() == mService.getSession()));

            // Highlight toggles
            menu.findItem(R.id.context_mute).setChecked(mUser.isMuted());
            menu.findItem(R.id.context_deafen).setChecked(mUser.isDeafened());
            menu.findItem(R.id.context_priority).setChecked(mUser.isPrioritySpeaker());
            menu.findItem(R.id.context_local_mute).setChecked(mUser.isLocalMuted());
            menu.findItem(R.id.context_ignore_messages).setChecked(mUser.isLocalIgnored());
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        try {
            boolean ban = false;
            switch (menuItem.getItemId()) {
                case R.id.context_send_message:
                    if(mChatTargetProvider.getChatTarget() != null &&
                            mChatTargetProvider.getChatTarget().getUser() != null &&
                            mChatTargetProvider.getChatTarget().getUser().equals(mUser)) {
                        mChatTargetProvider.setChatTarget(null);
                    } else {
                        mChatTargetProvider.setChatTarget(new ChatTargetProvider.ChatTarget(mUser));
                    }
                    break;
                case R.id.context_ban:
                    ban = true;
                case R.id.context_kick:
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(mContext);
                    alertBuilder.setTitle(R.string.user_menu_kick);
                    final EditText reasonField = new EditText(mContext);
                    reasonField.setHint(R.string.hint_reason);
                    alertBuilder.setView(reasonField);
                    final boolean finalBan = ban;
                    alertBuilder.setPositiveButton(R.string.user_menu_kick, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                mService.kickBanUser(mUser.getSession(), reasonField.getText().toString(), finalBan);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    alertBuilder.setNegativeButton(android.R.string.cancel, null);
                    alertBuilder.show();
                    break;
                case R.id.context_mute:
                    mService.setMuteDeafState(mUser.getSession(), !mUser.isMuted(), mUser.isDeafened());
                    break;
                case R.id.context_deafen:
                    mService.setMuteDeafState(mUser.getSession(), mUser.isMuted(), !mUser.isDeafened());
                    break;
                case R.id.context_move:
                    showChannelMoveDialog();
                    break;
                case R.id.context_priority:
                    mService.setPrioritySpeaker(mUser.getSession(), !mUser.isPrioritySpeaker());
                    break;
                case R.id.context_local_mute:
                    mUser.setLocalMuted(!mUser.isLocalMuted());
                    break;
                case R.id.context_ignore_messages:
                    mUser.setLocalIgnored(!mUser.isLocalIgnored());
                    break;
                case R.id.context_change_comment:
                    showUserComment(true);
                    break;
                case R.id.context_view_comment:
                    showUserComment(false);
                    break;
                case R.id.context_reset_comment:
                    new AlertDialog.Builder(mContext)
                            .setMessage(mContext.getString(R.string.confirm_reset_comment, mUser.getName()))
                            .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        mService.setUserComment(mUser.getSession(), "");
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    break;
//                case R.id.context_info:
//                    break;
                case R.id.context_register:
                    mService.registerUser(mUser.getSession());
                    break;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
        actionMode.finish(); // FIXME?
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {

    }

    private void showUserComment(final boolean edit) {
        Bundle args = new Bundle();
        args.putInt("session", mUser.getSession());
        args.putString("comment", mUser.getComment());
        args.putBoolean("editing", edit);
        UserCommentFragment fragment = (UserCommentFragment) Fragment.instantiate(mContext, UserCommentFragment.class.getName(), args);
        fragment.show(mFragmentManager, UserCommentFragment.class.getName());
    }

    private void showChannelMoveDialog() throws RemoteException {
        AlertDialog.Builder adb = new AlertDialog.Builder(mContext);
        adb.setTitle(R.string.user_menu_move);
        final List<Channel> channels = mService.getChannelList();
        List<CharSequence> channelNames = Lists.transform(channels, new Function<Channel, CharSequence>() {
            @Override
            public CharSequence apply(Channel channel) {
                return channel.getName();
            }
        });
        adb.setItems(channelNames.toArray(new CharSequence[channelNames.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Channel channel = channels.get(which);
                try {
                    mService.moveUserToChannel(mUser.getSession(), channel.getId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        adb.show();
    }
}
