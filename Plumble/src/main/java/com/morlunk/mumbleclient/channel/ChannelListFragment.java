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

        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);

        try {
            if(getService() != null && getService().isConnected() && getService().getSessionUser() != null) {
                User self = getService().getSessionUser();
                muteItem.setIcon(self.isSelfMuted() ? R.drawable.ic_action_microphone_muted : R.drawable.ic_action_microphone);
                deafenItem.setIcon(self.isSelfDeafened() ? R.drawable.ic_action_audio_muted : R.drawable.ic_action_audio);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

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

        // This breaks uMurmur ACL. Put in a fix based on server version perhaps?
        //menu.getMenu().findItem(R.id.menu_channel_add).setVisible((permissions & (Permissions.MakeChannel | Permissions.MakeTempChannel)) > 0);
        menu.getMenu().findItem(R.id.menu_channel_edit).setVisible((permissions & Permissions.Write) > 0);
        menu.getMenu().findItem(R.id.menu_channel_remove).setVisible((permissions & Permissions.Write) > 0);

        menu.show();
    }

    /**
     * Slides a PopupWindow containing a user menu underneath the passed view.
     */
    public void showUserMenu(final View view, final User user) {
        try {
            ChannelUserWindow userWindow = new ChannelUserWindow(getActivity(), getService(), getChildFragmentManager(), user, mTargetProvider);
            userWindow.showAsDropDown(view);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
}
