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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleObserver;
import com.morlunk.jumble.net.Permissions;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;
import com.morlunk.mumbleclient.view.PlumbleNestedListView;
import com.morlunk.mumbleclient.view.PlumbleNestedListView.OnNestedChildClickListener;
import com.morlunk.mumbleclient.view.PlumbleNestedListView.OnNestedGroupClickListener;

import java.util.ArrayList;
import java.util.List;

public class ChannelListFragment extends JumbleServiceFragment implements OnNestedChildClickListener, OnNestedGroupClickListener, ChannelListAdapter.ChannelMenuListener, CommentFragment.CommentFragmentListener {

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

	private IJumbleObserver mServiceObserver = new JumbleObserver() {
        @Override
        public void onDisconnected() throws RemoteException {
            mChannelView.setAdapter(null);
        }

        @Override
        public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {
            updateChannelList();
            if(getService().getSession() == user.getSession()) {
                scrollToChannel(newChannel.getId());
            }
        }

        @Override
		public void onChannelAdded(Channel channel) throws RemoteException {
			updateChannelList();
		}

		@Override
		public void onChannelRemoved(Channel channel) throws RemoteException {
			updateChannelList();
		}

        @Override
        public void onChannelStateUpdated(Channel channel) throws RemoteException {
            updateChannel(channel);
        }

        @Override
        public void onUserConnected(User user) throws RemoteException {
            updateChannelList();
        }

        @Override
        public void onUserRemoved(User user, String reason) throws RemoteException {
            removeUser(user);
        }

        @Override
        public void onUserStateUpdated(User user) throws RemoteException {
            updateUser(user);
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            updateUserTalking(user);
        }
	};

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(getActivity() != null)
                getActivity().supportInvalidateOptionsMenu(); // Update bluetooth menu item
        }
    };

	private PlumbleNestedListView mChannelView;
	private ChannelListAdapter mChannelListAdapter;
    private ChatTargetProvider mTargetProvider;
    private DatabaseProvider mDatabaseProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mTargetProvider = (ChatTargetProvider) getParentFragment();
        } catch (ClassCastException e) {
            throw new ClassCastException(getParentFragment().toString()+" must implement ChatTargetProvider");
        }
        try {
            mDatabaseProvider = (DatabaseProvider) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()+" must implement DatabaseProvider");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);

        // Get the UI views
        mChannelView = (PlumbleNestedListView) view.findViewById(R.id.channelUsers);
        mChannelView.setOnChildClickListener(this);
        mChannelView.setOnGroupClickListener(this);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(mChannelView);
        getActivity().registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
    }

    @Override
    public void onDetach() {
        getActivity().unregisterReceiver(mBluetoothReceiver);
        super.onDetach();
    }

    @Override
    public IJumbleObserver getServiceObserver() {
        return mServiceObserver;
    }

    @Override
    public void onServiceBound(IJumbleService service) {
        try {
            if(mChannelListAdapter == null)
                setupChannelList();
            else
                updateChannelList();
            scrollToUser(service.getSession());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
//
//        Mute/deaf opacity conflicts with color drawable mutation
//        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
//        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);
//
//        try {
//            if(getService() != null && getService().getSessionUser() != null && getService().isConnected()) {
//                User self = getService().getSessionUser();
//                muteItem.getIcon().mutate().setAlpha(self.isSelfMuted() ? 100 : 255);
//                deafenItem.getIcon().mutate().setAlpha(self.isSelfDeafened() ? 100 : 255);
//            }
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        }

        try {
            if(getService() != null) {
                MenuItem bluetoothItem = menu.findItem(R.id.menu_bluetooth);
                bluetoothItem.setChecked(getService().isBluetoothAvailable());
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_mute_button:
                try {
                    User self = getService().getSessionUser();

                    boolean muted = !self.isSelfMuted();
                    boolean deafened = self.isSelfDeafened();
                    deafened &= muted; // Undeafen if mute is off
                    self.setSelfMuted(muted);
                    self.setSelfDeafened(deafened);
                    getService().setSelfMuteDeafState(self.isSelfMuted(), self.isSelfDeafened());

                    getActivity().supportInvalidateOptionsMenu();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return true;
            case R.id.menu_deafen_button:
                try {
                    User self = getService().getSessionUser();

                    boolean deafened = self.isSelfDeafened();
                    self.setSelfDeafened(!deafened);
                    self.setSelfMuted(!deafened);
                    getService().setSelfMuteDeafState(self.isSelfDeafened(), self.isSelfDeafened());

                    getActivity().supportInvalidateOptionsMenu();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return true;
            case R.id.menu_search:
                return false;
            case R.id.menu_bluetooth:
                item.setChecked(!item.isChecked());
                try {
                    getService().setBluetoothEnabled(item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupChannelList() throws RemoteException {
        mChannelListAdapter = new ChannelListAdapter(getActivity(), mChannelView, getService(), mDatabaseProvider.getDatabase(), isShowingPinnedChannels());
        mChannelListAdapter.setChannelMenuListener(this);
        mChannelView.setAdapter(mChannelListAdapter);
		updateChannelList();
	}

	public void updateChannelList() throws RemoteException {
		mChannelListAdapter.updateChannelList();
		mChannelListAdapter.notifyDataSetChanged();
	}

	public void updateUser(User user) throws RemoteException {
		mChannelListAdapter.refreshUser(user);
	}

	public void updateChannel(Channel channel) throws RemoteException {
		if(channel.getDescription() != null || channel.getDescriptionHash() != null) {
//          TODO reimplement comment caching
//			mChannelListAdapter.commentsSeen.put(channel, mChannelListAdapter.database.isCommentSeen(
//				channel.getName(),
//				channel.getDescriptionHash() != null ? new String(channel.getDescriptionHash()) : channel.getDescription()));
		}
		updateChannelList();
	}

	public void updateUserTalking(User user) {
		mChannelListAdapter.refreshTalkingState(user);
	}

	/**
	 * Removes the user from the channel list.
	 *
	 * @param user
	 */
	public void removeUser(User user) {
        mChannelListAdapter.notifyDataSetChanged();
	}

	/**
	 * Scrolls to the passed channel.
	 */
	public void scrollToChannel(int channelId) {
		int channelPosition = mChannelListAdapter.getFlatGroupPosition(channelId);
		mChannelView.smoothScrollToPosition(channelPosition);
	}
	/**
	 * Scrolls to the passed user.
	 */
	public void scrollToUser(int userId) {
		int userPosition = mChannelListAdapter.getFlatChildPosition(userId);
		mChannelView.smoothScrollToPosition(userPosition);
	}

    public void setChatTarget(User chatTarget) {
		User oldTarget = chatTarget;
		if (mChannelListAdapter != null) {
            try {
                if (oldTarget != null)
                    mChannelListAdapter.refreshUser(oldTarget);
                mChannelListAdapter.refreshUser(chatTarget);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
		}
	}
	@Override
	public void onNestedChildClick(AdapterView<?> parent, View view, int groupId, int childPosition) {
        User user = mChannelListAdapter.getChild(groupId, childPosition);
        showUserMenu(view, user);
	}

	@Override
	public void onNestedGroupClick(AdapterView<?> parent, View view, int groupId) {
        try {
            getService().joinChannel(groupId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showChannelMenu(Channel channel, View anchor) {
        PopupMenu menu = new PopupMenu(getActivity(), anchor);
        menu.inflate(R.menu.channel_modify_menu);
        // TODO detect permissions
        ChannelPopupMenuListener menuListener = new ChannelPopupMenuListener(channel);
        menu.setOnMenuItemClickListener(menuListener);
        boolean targeted = mTargetProvider.getChatTarget() != null &&
                mTargetProvider.getChatTarget().getChannel() != null &&
                mTargetProvider.getChatTarget().getChannel().getId() == channel.getId();
        menu.getMenu().findItem(R.id.menu_channel_send_message).setChecked(targeted);

        try {
            long serverId = getService().getConnectedServer().getId();
            boolean pinned = mDatabaseProvider.getDatabase().isChannelPinned(serverId, channel.getId());
            menu.getMenu().findItem(R.id.menu_channel_pin).setChecked(pinned);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        int permissions = channel.getPermissions();
        menu.getMenu().findItem(R.id.menu_channel_add).setVisible((permissions & (Permissions.MakeChannel | Permissions.MakeTempChannel)) > 0);
        menu.getMenu().findItem(R.id.menu_channel_edit).setVisible((permissions & Permissions.Write) > 0);
        menu.getMenu().findItem(R.id.menu_channel_remove).setVisible((permissions & Permissions.Write) > 0);

        menu.show();
    }

    /**
     * Slides a PopupWindow containing a user menu underneath the passed view.
     */
    public void showUserMenu(final View view, final User user) {
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View menuView = inflater.inflate(R.layout.popup_menu, null, false);
        final GridView menuGrid = (GridView) menuView.findViewById(R.id.user_menu_grid);
        final PopupWindow popupWindow = new PopupWindow(menuView, view.getMeasuredWidth(), WindowManager.LayoutParams.WRAP_CONTENT, true);

        // Update menu grid when we receive updated permissions.
        final JumbleObserver observer = new JumbleObserver() {
            @Override
            public void onChannelPermissionsUpdated(Channel channel) throws RemoteException {
                List<ChannelMenuItem> items = getUserMenu(user);
                menuGrid.setAdapter(new PopupGridMenuAdapter(getActivity(), items));
            }
        };

        try {
            getService().registerObserver(observer);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        final List<ChannelMenuItem> menuItems;
        try {
            menuItems = getUserMenu(user);
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        }
        menuGrid.setAdapter(new PopupGridMenuAdapter(getActivity(), menuItems));
        menuGrid.setOnItemClickListener(new UserMenuListener(user, popupWindow));

        final Animation slideDown = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_down);
        final Animation slideUp = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_up);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                Handler handler = new Handler();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        popupWindow.dismiss(); // Automatically dismiss when hidden. Do this in main thread to avoid errors.
                    }
                });
            }
            @Override public void onAnimationRepeat(Animation animation) { }
        });

        popupWindow.setBackgroundDrawable(new ColorDrawable()); // Hack to fix touch events.
        popupWindow.setAnimationStyle(0); // No animations. We do our own.

        // We do some not-so-nice hacks here. Because we're animating the view inside the PopupWindow and not the window itself, we have to make our own method to dismiss the window when there is an outside touch.
        popupWindow.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Rect rect = new Rect();
                v.getHitRect(rect);
                if(event.getAction() == MotionEvent.ACTION_DOWN && !rect.contains((int)event.getX(), (int)event.getY())) {
                    menuView.startAnimation(slideUp); // Dismiss is done after animation, see above.
                    return true;
                }
                return false;
            }
        });

        // Unregister listener on dismiss
        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if(getService() != null)
                    try {
                        getService().unregisterObserver(observer);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
            }
        });

        // We only slide down the view inside of the PopupWindow so it clips to the PopupWindow.
        menuView.startAnimation(slideDown);
        popupWindow.showAsDropDown(view);
    }

    /**
     * Configures the menu for the provided user, enabling allowed features and disabling/hiding others.
     */
    private List<ChannelMenuItem> getUserMenu(User user) throws RemoteException {
        // TODO: maybe make these all class members, there's no point in re-instantiating every time we want to make a menu.
        List<ChannelMenuItem> menuItems = new ArrayList<ChannelMenuItem>();
        ChannelMenuItem kickItem = new ChannelMenuItem(KICK_ID, getActivity().getString(R.string.user_menu_kick), R.drawable.ic_action_delete_dark);
        ChannelMenuItem banItem = new ChannelMenuItem(BAN_ID, getActivity().getString(R.string.user_menu_ban), R.drawable.ic_action_error);
        ChannelMenuItem muteItem = new ChannelMenuItem(MUTE_ID, getActivity().getString(R.string.user_menu_mute), R.drawable.ic_action_microphone);
        ChannelMenuItem deafItem = new ChannelMenuItem(DEAFEN_ID, getActivity().getString(R.string.user_menu_deafen), R.drawable.ic_action_headphones);
        ChannelMenuItem moveItem = new ChannelMenuItem(MOVE_ID, getActivity().getString(R.string.user_menu_move), R.drawable.ic_action_send);
        ChannelMenuItem priorityItem = new ChannelMenuItem(PRIORITY_ID, getActivity().getString(R.string.user_menu_priority_speaker), R.drawable.ic_action_audio_on);
        ChannelMenuItem localMuteItem = new ChannelMenuItem(LOCAL_MUTE_ID, getActivity().getString(R.string.user_menu_local_mute), R.drawable.ic_action_audio_muted);
        ChannelMenuItem ignoreMessagesItem = new ChannelMenuItem(IGNORE_MESSAGES_ID, getActivity().getString(R.string.user_menu_ignore_messages), R.drawable.ic_action_bad);
        ChannelMenuItem changeCommentItem = new ChannelMenuItem(CHANGE_COMMENT_ID, getActivity().getString(R.string.user_menu_change_comment), R.drawable.ic_action_edit_dark);
        ChannelMenuItem viewCommentItem = new ChannelMenuItem(VIEW_COMMENT_ID, getActivity().getString(R.string.user_menu_view_comment), R.drawable.ic_action_comment);
        ChannelMenuItem resetCommentItem = new ChannelMenuItem(RESET_COMMENT_ID, getActivity().getString(R.string.user_menu_reset_comment), R.drawable.ic_action_comment);
        ChannelMenuItem informationItem = new ChannelMenuItem(USER_INFORMATION_ID, getActivity().getString(R.string.user_menu_information), R.drawable.ic_action_info_dark);
        ChannelMenuItem sendMessageItem = new ChannelMenuItem(SEND_MESSAGE_ID, getActivity().getString(R.string.user_menu_send_message), R.drawable.ic_action_chat_dark);
        ChannelMenuItem registerItem = new ChannelMenuItem(REGISTER_ID, getActivity().getString(R.string.user_menu_register), R.drawable.ic_registered);

        boolean self = user.getSession() == getService().getSession();

        if((getService().getPermissions() & (Permissions.Kick | Permissions.Ban | Permissions.Write)) > 0)
            menuItems.add(kickItem);

        if((getService().getPermissions() & (Permissions.Ban | Permissions.Write)) > 0)
            menuItems.add(banItem);

        if((getService().getPermissions() & Permissions.Move) > 0)
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

        registerItem.enabled = user.getUserId() < 0 && (user.getHash() != null && !user.getHash().isEmpty()) && (getService().getPermissions() & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0; // Enable if we can register, and the user isn't registered.
        registerItem.title = user.getUserId() < 0 ? getActivity().getString(R.string.user_menu_register) : getActivity().getString(R.string.user_menu_registered); // Say 'registered' if registered.

        Channel channel = getService().getChannel(user.getChannelId());
        int channelPermissions = channel.getPermissions();

        if(channelPermissions == 0) {
            getService().requestPermissions(channel.getId());
            if(channel.getId() == 0)
                channelPermissions = getService().getPermissions();
            else
                channelPermissions = Permissions.All;
            channel.setPermissions(channelPermissions);
        }

        muteItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0 && ((user.getSession() != getService().getSession()) || user.isMuted() || user.isSuppressed()));
        deafItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0 && ((user.getSession() != getService().getSession()) || user.isDeafened()));
        priorityItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.MuteDeafen)) > 0);
        sendMessageItem.enabled = ((channelPermissions & (Permissions.Write | Permissions.TextMessage)) > 0);
        informationItem.enabled = (((getService().getPermissions() & (Permissions.Write | Permissions.Register))) > 0 || (channelPermissions & (Permissions.Write | Permissions.Enter)) > 0 || (user.getSession() == getService().getSession()));

        kickItem.enabled = !self;
        banItem.enabled = !self;
        moveItem.enabled = !self;
        localMuteItem.enabled = !self;
        ignoreMessagesItem.enabled = !self;
        resetCommentItem.enabled = user.getCommentHash() != null && !user.getCommentHash().isEmpty() && (getService().getPermissions() & (Permissions.Move | Permissions.Write)) > 0;
        viewCommentItem.enabled = (user.getComment() != null && !user.getComment().isEmpty()) || (user.getCommentHash() != null && !user.getCommentHash().isEmpty());

        // Highlight toggles
        muteItem.toggled = user.isMuted();
        deafItem.toggled = user.isDeafened();
        priorityItem.toggled = user.isPrioritySpeaker();
        localMuteItem.toggled = user.isLocalMuted();
        ignoreMessagesItem.toggled = user.isLocalIgnored();
        sendMessageItem.toggled = mTargetProvider.getChatTarget() != null && mTargetProvider.getChatTarget().getUser() != null ? mTargetProvider.getChatTarget().getUser().getSession() == user.getSession() : false;

        return menuItems;
    }

    private boolean isShowingPinnedChannels() {
        return getArguments().getBoolean("pinned");
    }

    @Override
    public void onCommentChanged(int session, String comment) {
        try {
            getService().setUserComment(session, comment);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
            int filterColor = item.toggled ? getResources().getColor(R.color.holo_blue_light) : title.getCurrentTextColor();

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

    private class ChannelPopupMenuListener implements PopupMenu.OnMenuItemClickListener {

        private Channel mChannel;

        public ChannelPopupMenuListener(Channel channel) {
            mChannel = channel;
        }

        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            boolean adding = false;
            switch(menuItem.getItemId()) {
                case R.id.menu_channel_add:
                    adding = true;
                case R.id.menu_channel_edit:
                    ChannelEditFragment addFragment = new ChannelEditFragment();
                    Bundle args = new Bundle();
                    if(adding) args.putInt("parent", mChannel.getId());
                    else args.putInt("channel", mChannel.getId());
                    args.putBoolean("adding", adding);
                    addFragment.setArguments(args);
                    addFragment.show(getChildFragmentManager(), "ChannelAdd");
                    return true;
                case R.id.menu_channel_remove:
                    AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
                    adb.setTitle(R.string.confirm);
                    adb.setMessage(R.string.confirm_delete_channel);
                    adb.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                getService().removeChannel(mChannel.getId());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    adb.setNegativeButton(android.R.string.cancel, null);
                    adb.show();
                    return true;
                case R.id.menu_channel_send_message:
                    if(mTargetProvider.getChatTarget() != null &&
                            mTargetProvider.getChatTarget().getChannel() != null &&
                            mTargetProvider.getChatTarget().getChannel().getId() == mChannel.getId())
                        mTargetProvider.setChatTarget(null);
                    else
                        mTargetProvider.setChatTarget(new ChatTargetProvider.ChatTarget(mChannel));
                    return true;
                case R.id.menu_channel_pin:
                    try {
                        long serverId = getService().getConnectedServer().getId();
                        boolean pinned = mDatabaseProvider.getDatabase().isChannelPinned(serverId, mChannel.getId());
                        if(!pinned) mDatabaseProvider.getDatabase().addPinnedChannel(serverId, mChannel.getId());
                        else mDatabaseProvider.getDatabase().removePinnedChannel(serverId, mChannel.getId());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    return true;
            }
            return false;
        }
    }

    private class UserMenuListener implements GridView.OnItemClickListener {

        private User mUser;
        private PopupWindow mWindow;

        public UserMenuListener(User user, PopupWindow window) {
            mUser = user;
            mWindow = window;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
                boolean ban = false;
                switch ((int)id) {
                    case BAN_ID:
                        ban = true;
                    case KICK_ID:
                        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
                        alertBuilder.setTitle(R.string.user_menu_kick);
                        final EditText reasonField = new EditText(getActivity());
                        reasonField.setHint(R.string.hint_reason);
                        alertBuilder.setView(reasonField);
                        final boolean finalBan = ban;
                        alertBuilder.setPositiveButton(R.string.user_menu_kick, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    getService().kickBanUser(mUser.getSession(), reasonField.getText().toString(), finalBan);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        alertBuilder.setNegativeButton(android.R.string.cancel, null);
                        alertBuilder.show();
                        break;
                    case MUTE_ID:
                        getService().setMuteDeafState(mUser.getSession(), !mUser.isMuted(), mUser.isDeafened());
                        break;
                    case DEAFEN_ID:
                        getService().setMuteDeafState(mUser.getSession(), mUser.isMuted(), !mUser.isDeafened());
                        break;
                    case MOVE_ID:
                        showChannelMoveDialog();
                        break;
                    case PRIORITY_ID:
                        getService().setPrioritySpeaker(mUser.getSession(), !mUser.isPrioritySpeaker());
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
                        new AlertDialog.Builder(getActivity())
                                .setMessage(getString(R.string.confirm_reset_comment, mUser.getName()))
                                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            getService().setUserComment(mUser.getSession(), "");
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
                        getService().registerUser(mUser.getSession());
                        break;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            mWindow.dismiss();
        }

        private void showUserComment(final boolean edit) {
            if(mUser.getCommentHash() != null && mUser.getComment() == null) {
                try {
                    final ProgressDialog dialog = ProgressDialog.show(getActivity(), null, getString(R.string.loading), true, true);
                    // Send a RequestBlob to the server to ask for the comment. Register a listener to show the edit dialog when we get the response.
                    getService().registerObserver(new JumbleObserver() {
                        @Override
                        public void onUserStateUpdated(User user) throws RemoteException {
                            if(user.getSession() == mUser.getSession() && user.getComment() != null) {
                                getService().unregisterObserver(this); // TODO if we don't get a response find a way to unregister this anyway
                                dialog.dismiss();
                                showUserComment(edit);
                            }
                        }
                    });
                    getService().requestComment(mUser.getSession());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            } else {
                CommentFragment editCommentFragment = CommentFragment.newInstance(mUser.getSession(), mUser.getComment(), edit);
                editCommentFragment.show(getChildFragmentManager(), CommentFragment.class.getName());
            }
        }

        private void showChannelMoveDialog() throws RemoteException {
            AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
            adb.setTitle(R.string.user_menu_move);
            final List<Channel> channels = getService().getChannelList();
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
                        getService().moveUserToChannel(mUser.getSession(), channel.getId());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
            adb.show();
        }
    }
}
