/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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
import android.support.v7.widget.PopupMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.comment.UserCommentFragment;
import com.morlunk.mumbleclient.service.PlumbleService;
import com.morlunk.mumbleclient.util.ModelUtils;

import java.util.List;

/**
 * Created by andrew on 25/09/15.
 */
public class UserMenuProvider implements View.OnClickListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    private final Context mContext;
    private final IUser mUser;
    private final PlumbleService.PlumbleBinder mService;
    private final FragmentManager mFragmentManager;
    private final Listener mListener;
    private final JumbleObserver mPermissionObserver = new JumbleObserver() {
        @Override
        public void onUserStateUpdated(IUser user) throws RemoteException {
            if (user.getSession() == mUser.getSession())
                configureMenu(mMenu);
        }

        @Override
        public void onChannelPermissionsUpdated(IChannel channel) throws RemoteException {
            if (mMenu != null)
                configureMenu(mMenu);
        }
    };
    private Menu mMenu;

    public UserMenuProvider(Context context, IUser user, PlumbleService.PlumbleBinder service,
                            FragmentManager fragmentManager, Listener listener) {
        mContext = context;
        mUser = user;
        mService = service;
        mFragmentManager = fragmentManager;
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        PopupMenu menu = new PopupMenu(mContext, v);
        menu.inflate(R.menu.context_user);
        menu.setOnMenuItemClickListener(this);
        menu.setOnDismissListener(this);
        mMenu = menu.getMenu();

        try {
            // Observer permissions changes, configure menu when we receive an update
            mService.registerObserver(mPermissionObserver);
            // Request permissions update from server, if we don't have channel permissions
            IChannel channel = mUser.getChannel();
            if (channel != null && channel.getPermissions() == 0) {
                mService.requestPermissions(channel.getId());
            } else {
                configureMenu(mMenu);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        menu.show();
    }

    /**
     * Updates the menu's items to reflect the current user state and permissions.
     * @param menu A menu inflated from R.menu.context_user.
     */
    public void configureMenu(Menu menu) {
        try {
            // Use permission data to determine the actions available.
            boolean self = mUser.getSession() == mService.getSession();
            int perms = mService.getPermissions();
            IChannel channel = mUser.getChannel();
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
                            (perms & (Permissions.Move | Permissions.Write)) > 0);
            menu.findItem(R.id.context_view_comment).setVisible(
                    (mUser.getComment() != null && !mUser.getComment().isEmpty()) ||
                            (mUser.getCommentHash() != null));
            menu.findItem(R.id.context_register).setVisible(mUser.getUserId() < 0 &&
                    (mUser.getHash() != null && !mUser.getHash().isEmpty()) &&
                    (perms & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0);
            menu.findItem(R.id.context_local_mute).setVisible(!self);
            menu.findItem(R.id.context_ignore_messages).setVisible(!self);

            // TODO info
//            informationItem.enabled = (((perms & (Permissions.Write | Permissions.Register))) > 0 || (channelPermissions & (Permissions.Write | Permissions.Enter)) > 0 || (mUser.getSession() == mService.getSession()));

            // Highlight toggles
            menu.findItem(R.id.context_mute).setChecked(mUser.isMuted() || mUser.isSuppressed());
            menu.findItem(R.id.context_deafen).setChecked(mUser.isDeafened());
            menu.findItem(R.id.context_priority).setChecked(mUser.isPrioritySpeaker());
            menu.findItem(R.id.context_local_mute).setChecked(mUser.isLocalMuted());
            menu.findItem(R.id.context_ignore_messages).setChecked(mUser.isLocalIgnored());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void showUserComment(final boolean edit) throws RemoteException {
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
        final List<IChannel> channels = ModelUtils.getChannelList(mService.getRootChannel());
        final CharSequence[] channelNames = new CharSequence[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            channelNames[i] = channels.get(i).getName();
        }
        adb.setItems(channelNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                IChannel channel = channels.get(which);
                try {
                    mService.moveUserToChannel(mUser.getSession(), channel.getId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        adb.show();
    }

    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        try {
            switch (menuItem.getItemId()) {
                case R.id.context_ban:
                case R.id.context_kick:
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(mContext);
                    alertBuilder.setTitle(R.string.user_menu_kick);
                    final EditText reasonField = new EditText(mContext);
                    reasonField.setHint(R.string.hint_reason);
                    alertBuilder.setView(reasonField);
                    alertBuilder.setPositiveButton(R.string.user_menu_kick, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                mService.kickBanUser(mUser.getSession(),
                                        reasonField.getText().toString(), menuItem.getItemId() == R.id.context_ban);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    alertBuilder.setNegativeButton(android.R.string.cancel, null);
                    alertBuilder.show();
                    break;
                case R.id.context_mute:
                    mService.setMuteDeafState(mUser.getSession(), !(mUser.isMuted() || mUser.isSuppressed()), mUser.isDeafened());
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
                    mListener.onLocalUserStateUpdated(mUser);
                    break;
                case R.id.context_ignore_messages:
                    mUser.setLocalIgnored(!mUser.isLocalIgnored());
                    mListener.onLocalUserStateUpdated(mUser);
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
                default:
                    return false;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void onDismiss(PopupMenu popupMenu) {
        try {
            mService.unregisterObserver(mPermissionObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public interface Listener {
        /**
         * Called when a user's local state has changed in response to an action in this menu.
         * i.e. local mute, ignore changes.
         */
        void onLocalUserStateUpdated(IUser user);
    }
}
