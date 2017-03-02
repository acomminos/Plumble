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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.PopupMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.comment.UserCommentFragment;
import com.morlunk.mumbleclient.service.PlumbleService;
import com.morlunk.mumbleclient.util.ModelUtils;

import java.util.List;

/**
 * Created by andrew on 19/11/15.
 */
public class UserMenu implements PermissionsPopupMenu.IOnMenuPrepareListener, PopupMenu.OnMenuItemClickListener {
    private final Context mContext;
    private final IUser mUser;
    private final PlumbleService mService;
    private final FragmentManager mFragmentManager;
    private final IUserLocalStateListener mStateListener;

    public UserMenu(Context context, IUser user, PlumbleService service,
                    FragmentManager fragmentManager, IUserLocalStateListener stateListener) {
        mContext = context;
        mUser = user;
        mService = service;
        mFragmentManager = fragmentManager;
        mStateListener = stateListener;
    }

    @Override
    public void onMenuPrepare(Menu menu, int permissions) {
        // Use permission data to determine the actions available.
        boolean self = mUser.getSession() == mService.getSessionId();
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
//            informationItem.enabled = (((perms & (Permissions.Write | Permissions.Register))) > 0 || (channelPermissions & (Permissions.Write | Permissions.Enter)) > 0 || (mUser.getSessionId() == mService.getSessionId()));

        // Highlight toggles
        menu.findItem(R.id.context_mute).setChecked(mUser.isMuted() || mUser.isSuppressed());
        menu.findItem(R.id.context_deafen).setChecked(mUser.isDeafened());
        menu.findItem(R.id.context_priority).setChecked(mUser.isPrioritySpeaker());
        menu.findItem(R.id.context_local_mute).setChecked(mUser.isLocalMuted());
        menu.findItem(R.id.context_ignore_messages).setChecked(mUser.isLocalIgnored());
    }

    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
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
                        mService.kickBanUser(mUser.getSession(),
                                reasonField.getText().toString(), menuItem.getItemId() == R.id.context_ban);
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
                mStateListener.onLocalUserStateUpdated(mUser);
                break;
            case R.id.context_ignore_messages:
                mUser.setLocalIgnored(!mUser.isLocalIgnored());
                mStateListener.onLocalUserStateUpdated(mUser);
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
                                mService.setUserComment(mUser.getSession(), "");
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
        return true;
    }

    private void showUserComment(final boolean edit) {
        Bundle args = new Bundle();
        args.putInt("session", mUser.getSession());
        args.putString("comment", mUser.getComment());
        args.putBoolean("editing", edit);
        UserCommentFragment fragment = (UserCommentFragment) Fragment.instantiate(mContext, UserCommentFragment.class.getName(), args);
        fragment.show(mFragmentManager, UserCommentFragment.class.getName());
    }

    private void showChannelMoveDialog() {
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
                mService.moveUserToChannel(mUser.getSession(), channel.getId());
            }
        });
        adb.show();
    }

    public void showPopup(View anchor) {
        PermissionsPopupMenu popupMenu = new PermissionsPopupMenu(mContext, anchor,
                R.menu.context_user, this, this, mUser.getChannel(), mService);
        popupMenu.show();
    }

    /**
     * A listener notified whenever the user's local state changes.
     */
    public interface IUserLocalStateListener {
        void onLocalUserStateUpdated(IUser user);
    }
}
