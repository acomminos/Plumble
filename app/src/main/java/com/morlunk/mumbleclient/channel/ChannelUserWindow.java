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
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.channel.comment.UserCommentFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * A popup window providing access to various actions that can be performed on a user.
 * Created by andrew on 27/02/14.
 */
public class ChannelUserWindow extends PopupWindow implements GridView.OnItemClickListener {
    public static final int KICK_ID = 0,
            BAN_ID = 1,
            MUTE_ID = 2,
            DEAFEN_ID = 3,
            MOVE_ID = 4,
            PRIORITY_ID = 5,
            LOCAL_MUTE_ID = 6,
            IGNORE_MESSAGES_ID = 7,
            CHANGE_COMMENT_ID = 8,
            VIEW_COMMENT_ID = 9,
            RESET_COMMENT_ID = 10,
            SEND_MESSAGE_ID = 11,
            USER_INFORMATION_ID = 12,
            REGISTER_ID = 13;

    private Context mContext;
    private IJumbleService mService;
    private FragmentManager mFragmentManager;
    private User mUser;
    private ChatTargetProvider mTargetProvider;
    private GridView mGridView;

    private Animation mSlideInAnimation;
    private Animation mSlideOutAnimation;

    // Update menu grid when we receive updated permissions.
    private JumbleObserver mPermissionObserver = new JumbleObserver() {
        @Override
        public void onChannelPermissionsUpdated(Channel channel) throws RemoteException {
            configureMenu(); // Reconfigure menu to account for new permissions
        }
    };

    /**
     * We use this static instantiator to fix a bug on android 2.3 devices where the default constructor
     * of PopupWindow does not work due to the existence of a null contentView.
     */
    public static ChannelUserWindow instantiate(Context context, IJumbleService service, FragmentManager fragmentManager, User user, ChatTargetProvider targetProvider) throws RemoteException {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View menuView = inflater.inflate(R.layout.popup_menu, null, false);
        return new ChannelUserWindow(menuView, context, service, fragmentManager, user, targetProvider);
    }

    private ChannelUserWindow(View view, Context context, IJumbleService service, FragmentManager fragmentManager, User user, ChatTargetProvider targetProvider) throws RemoteException {
        super(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        mContext = context;
        mService = service;
        mFragmentManager = fragmentManager;
        mUser = user;
        mTargetProvider = targetProvider;

        mSlideInAnimation = AnimationUtils.loadAnimation(mContext, R.anim.slide_down);
        mSlideOutAnimation = AnimationUtils.loadAnimation(mContext, R.anim.slide_up);

        mGridView = (GridView) getContentView().findViewById(R.id.user_menu_grid);
        mGridView.setOnItemClickListener(this);

        setBackgroundDrawable(new ColorDrawable()); // Hack to fix touch events.
        setAnimationStyle(0); // We perform our own animations on the content view.

        mSlideOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Handler handler = new Handler();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismiss(); // Automatically dismiss when hidden. Do this in main thread to avoid errors.
                    }
                });
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // We do some not-so-nice hacks here. Because we're animating the view inside the PopupWindow and not the window itself, we have to make our own method to dismiss the window when there is an outside touch.
        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Rect rect = new Rect();
                v.getHitRect(rect);
                if (event.getAction() == MotionEvent.ACTION_DOWN &&
                        !rect.contains((int) event.getX(), (int) event.getY())) {
                    // Make sure we only play the animation once.
                    if (getContentView().getAnimation() == null ||
                            getContentView().getAnimation().hasEnded())
                        getContentView().startAnimation(mSlideOutAnimation); // Dismiss is done after animation, see above.
                    return true;
                }
                return false;
            }
        });

        configureMenu();
    }

    @Override
    public void showAsDropDown(View anchor) {
        super.showAsDropDown(anchor);
        // We only slide down the view inside of the PopupWindow so it clips to the PopupWindow.
        getContentView().startAnimation(mSlideInAnimation);
        try {
            mService.registerObserver(mPermissionObserver);
            updateChannelPermissions();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        try {
            mService.registerObserver(mPermissionObserver);
            updateChannelPermissions();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        try {
            mService.unregisterObserver(mPermissionObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Asks the server for the current channel permissions if we don't have any.
     * @return true if we asked the server for permissions.
     */
    private boolean updateChannelPermissions() throws RemoteException {
        Channel channel = mService.getChannel(mUser.getChannelId());
        int channelPermissions = channel.getPermissions();
        if(channelPermissions == 0) {
            // We'll create the menu once we get the proper permissions.
            mService.requestPermissions(channel.getId());
            return true;
        }
        return false;
    }

    /**
     * Configures the menu for the provided user, enabling allowed features and disabling/hiding others.
     * Sets up the grid adapter and its items.
     */
    private void configureMenu() throws RemoteException {
        List<ChannelMenuItem> menuItems = new ArrayList<ChannelMenuItem>();
        ChannelMenuItem kickItem = new ChannelMenuItem(KICK_ID, mContext.getString(R.string.user_menu_kick), R.drawable.ic_action_delete_dark);
        ChannelMenuItem banItem = new ChannelMenuItem(BAN_ID, mContext.getString(R.string.user_menu_ban), R.drawable.ic_action_error);
        ChannelMenuItem muteItem = new ChannelMenuItem(MUTE_ID, mContext.getString(R.string.user_menu_mute), R.drawable.ic_action_microphone);
        ChannelMenuItem deafItem = new ChannelMenuItem(DEAFEN_ID, mContext.getString(R.string.user_menu_deafen), R.drawable.ic_action_headphones);
        ChannelMenuItem moveItem = new ChannelMenuItem(MOVE_ID, mContext.getString(R.string.user_menu_move), R.drawable.ic_action_send);
        ChannelMenuItem priorityItem = new ChannelMenuItem(PRIORITY_ID, mContext.getString(R.string.user_menu_priority_speaker), R.drawable.ic_action_audio_on);
        ChannelMenuItem localMuteItem = new ChannelMenuItem(LOCAL_MUTE_ID, mContext.getString(R.string.user_menu_local_mute), R.drawable.ic_action_audio_muted);
        ChannelMenuItem ignoreMessagesItem = new ChannelMenuItem(IGNORE_MESSAGES_ID, mContext.getString(R.string.user_menu_ignore_messages), R.drawable.ic_action_bad);
        ChannelMenuItem changeCommentItem = new ChannelMenuItem(CHANGE_COMMENT_ID, mContext.getString(R.string.user_menu_change_comment), R.drawable.ic_action_edit_dark);
        ChannelMenuItem viewCommentItem = new ChannelMenuItem(VIEW_COMMENT_ID, mContext.getString(R.string.user_menu_view_comment), R.drawable.ic_action_comment);
        ChannelMenuItem resetCommentItem = new ChannelMenuItem(RESET_COMMENT_ID, mContext.getString(R.string.user_menu_reset_comment), R.drawable.ic_action_comment);
        ChannelMenuItem informationItem = new ChannelMenuItem(USER_INFORMATION_ID, mContext.getString(R.string.user_menu_information), R.drawable.ic_action_info_dark);
        ChannelMenuItem sendMessageItem = new ChannelMenuItem(SEND_MESSAGE_ID, mContext.getString(R.string.user_menu_send_message), R.drawable.ic_action_chat_dark);
        ChannelMenuItem registerItem = new ChannelMenuItem(REGISTER_ID, mContext.getString(R.string.user_menu_register), R.drawable.ic_registered);

        boolean self = mUser.getSession() == mService.getSession();

        if((mService.getPermissions() & (Permissions.Kick | Permissions.Ban | Permissions.Write)) > 0)
            menuItems.add(kickItem);

        if((mService.getPermissions() & (Permissions.Ban | Permissions.Write)) > 0)
            menuItems.add(banItem);

        if((mService.getPermissions() & Permissions.Move) > 0)
            menuItems.add(moveItem);

        menuItems.add(muteItem);
        menuItems.add(deafItem);
        menuItems.add(priorityItem);
        menuItems.add(localMuteItem);
        menuItems.add(ignoreMessagesItem);

        if(self) {
            menuItems.add(changeCommentItem);
        } else {
            menuItems.add(viewCommentItem);
            menuItems.add(resetCommentItem);
        }

//        menuItems.add(informationItem); TODO
        menuItems.add(sendMessageItem);

        menuItems.add(registerItem);

        registerItem.enabled = mUser.getUserId() < 0 && (mUser.getHash() != null && !mUser.getHash().isEmpty()) && (mService.getPermissions() & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0; // Enable if we can register, and the user isn't registered.
        registerItem.title = mUser.getUserId() < 0 ? mContext.getString(R.string.user_menu_register) : mContext.getString(R.string.user_menu_registered); // Say 'registered' if registered.

        Channel channel = mService.getChannel(mUser.getChannelId());
        int channelPermissions = channel.getPermissions();
        if(channel.getId() == 0) channelPermissions = mService.getPermissions();

        muteItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0 && ((mUser.getSession() != mService.getSession()) || mUser.isMuted() || mUser.isSuppressed()));
        deafItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0 && ((mUser.getSession() != mService.getSession()) || mUser.isDeafened()));
        priorityItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0);
        sendMessageItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.TextMessage)) > 0);
        informationItem.enabled = (((mService.getPermissions() & (Permissions.Write | Permissions.Register))) > 0 || (channelPermissions & (Permissions.Write | Permissions.Enter)) > 0 || (mUser.getSession() == mService.getSession()));

        kickItem.enabled = !self;
        banItem.enabled = !self;
        moveItem.enabled = !self;
        localMuteItem.enabled = !self;
        ignoreMessagesItem.enabled = !self;
        resetCommentItem.enabled = mUser.getCommentHash() != null && !mUser.getCommentHash().isEmpty() && (mService.getPermissions() & (Permissions.Move | Permissions.Write)) > 0;
        viewCommentItem.enabled = (mUser.getComment() != null && !mUser.getComment().isEmpty()) || (mUser.getCommentHash() != null && !mUser.getCommentHash().isEmpty());

        // Highlight toggles
        muteItem.toggled = mUser.isMuted();
        deafItem.toggled = mUser.isDeafened();
        priorityItem.toggled = mUser.isPrioritySpeaker();
        localMuteItem.toggled = mUser.isLocalMuted();
        ignoreMessagesItem.toggled = mUser.isLocalIgnored();
        sendMessageItem.toggled = mTargetProvider.getChatTarget() != null && mTargetProvider.getChatTarget().getUser() != null && mTargetProvider.getChatTarget().getUser().getSession() == mUser.getSession();

        mGridView.setAdapter(new PopupGridMenuAdapter(mContext, menuItems));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            boolean ban = false;
            switch ((int)id) {
                case BAN_ID:
                    ban = true;
                case KICK_ID:
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
                case MUTE_ID:
                    mService.setMuteDeafState(mUser.getSession(), !mUser.isMuted(), mUser.isDeafened());
                    break;
                case DEAFEN_ID:
                    mService.setMuteDeafState(mUser.getSession(), mUser.isMuted(), !mUser.isDeafened());
                    break;
                case MOVE_ID:
                    showChannelMoveDialog();
                    break;
                case PRIORITY_ID:
                    mService.setPrioritySpeaker(mUser.getSession(), !mUser.isPrioritySpeaker());
                    break;
                case LOCAL_MUTE_ID:
                    mUser.setLocalMuted(!mUser.isLocalMuted());
                    break;
                case IGNORE_MESSAGES_ID:
                    mUser.setLocalIgnored(!mUser.isLocalIgnored());
                    break;
                case CHANGE_COMMENT_ID:
                    showUserComment(true);
                    break;
                case VIEW_COMMENT_ID:
                    showUserComment(false);
                    break;
                case RESET_COMMENT_ID:
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
                case USER_INFORMATION_ID:
                    break;
                case SEND_MESSAGE_ID:
                    if(mTargetProvider.getChatTarget() != null &&
                            mTargetProvider.getChatTarget().getUser() != null &&
                            mTargetProvider.getChatTarget().getUser().getSession() == mUser.getSession()) {
                        mTargetProvider.setChatTarget(null); // If selecting the same user, deselect target.
                    } else {
                        ChatTargetProvider.ChatTarget target = new ChatTargetProvider.ChatTarget(mUser);
                        mTargetProvider.setChatTarget(target);
                    }
                    break;
                case REGISTER_ID:
                    mService.registerUser(mUser.getSession());
                    break;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        dismiss();
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

    private class ChannelMenuItem {
        public int id;
        public String title;
        public int icon;
        public boolean enabled = true;
        public boolean toggled;

        public ChannelMenuItem(int id, String title, int icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }
    }

    private class PopupGridMenuAdapter extends ArrayAdapter<ChannelMenuItem> {

        public PopupGridMenuAdapter(Context context, List<ChannelMenuItem> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if(v == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                v = inflater.inflate(R.layout.popup_user_menu_item, parent, false);
            }

            ImageView icon = (ImageView) v.findViewById(R.id.user_menu_item_icon);
            TextView title = (TextView) v.findViewById(R.id.user_menu_item_title);

            ChannelMenuItem item = getItem(position);

            icon.setImageResource(item.icon);
            title.setText(item.title);

            // Retrieve the title color and manipulate it
            int filterColor = item.toggled ? getContext().getResources().getColor(R.color.holo_blue_light) : title.getCurrentTextColor();

            // If the item isn't enabled, dim.
            if(!item.enabled) {
                filterColor &= 0x00FFFFFF; // First, remove opacity data.
                filterColor |= 0x80000000; // Then use an OR operation to apply 50% alpha.
            }

            // Apply #33b5e5 (R.I.P. #holoyolo) color filter when toggled. Use the title's color (likely ?textColorSecondary) for the icon otherwise. TODO use defined ?highlightColor.
            icon.setColorFilter(filterColor, PorterDuff.Mode.MULTIPLY);
            title.setTextColor(filterColor);

            return v;
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).enabled;
        }
    }
}
