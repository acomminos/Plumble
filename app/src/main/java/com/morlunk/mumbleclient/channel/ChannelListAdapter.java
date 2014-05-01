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

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.view.PlumbleNestedAdapter;
import com.morlunk.mumbleclient.view.PlumbleNestedListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrew on 31/07/13.
 */
public class ChannelListAdapter extends PlumbleNestedAdapter<Channel, User> {

    /**
     * Gets called when the channel menu needs to be shown.
     */
    public interface ChannelMenuListener {
        public void showChannelMenu(Channel channel, View anchor);
    }

    private IJumbleService mService;
    private PlumbleNestedListView mListView;
    private ChannelMenuListener mMenuListener;
    private PlumbleDatabase mDatabase;

    private SparseArray<Channel> mChannels = new SparseArray<Channel>();
    private SparseArray<User> mUsers = new SparseArray<User>();
    private List<Integer> mRootChannels = new ArrayList<Integer>();

    private boolean mShowPinnedOnly;

    public ChannelListAdapter(Context context, PlumbleNestedListView listView, IJumbleService service, PlumbleDatabase database, boolean showPinnedOnly) {
        super(context);
        mService = service;
        mDatabase = database;
        mListView = listView;
        mShowPinnedOnly = showPinnedOnly;
    }

    /**
     * Fetches a new list of channels from the service.
     */
    public void updateChannelList() throws RemoteException {
        if(!mService.isConnected()) return;

        mChannels.clear();
        mUsers.clear();

        List<Channel> channels = mService.getChannelList();
        List<User> users = mService.getUserList();
        mRootChannels = new ArrayList<Integer>();

        if(mShowPinnedOnly) {
            mRootChannels = mDatabase.getPinnedChannels(mService.getConnectedServer().getId());
        } else {
            mRootChannels.add(0);
        }

        for(Channel channel : channels)
            mChannels.put(channel.getId(), channel);
        for(User user : users)
            mUsers.put(user.getSession(), user);
    }

    public void refreshUser(User user) throws RemoteException {
        int position = getVisibleFlatChildPosition(user.getSession());
        if(position < 0)
            return;

        View userView = mListView.getChildAt(position - mListView.getFirstVisiblePosition());

        // Update comment state TODO reimplement
//        if (user.getComment() != null
//                || user.getCommentHash() != null
//                && !service.isConnectedServerPublic()) {
//            commentsSeen.put(user, database.isCommentSeen(
//                    user.name,
//                    user.commentHash != null ? user.commentHash
//                            .toStringUtf8() : user.comment));
//        }

        if (userView != null && userView.isShown() && userView.getTag() != null && userView.getTag().equals(user))
            refreshElements(userView, user);
    }

    public void refreshTalkingState(User user) {
        int position = getVisibleFlatChildPosition(user.getSession());
        if(position < 0)
            return;

        View userView = mListView.getChildAt(position - mListView.getFirstVisiblePosition());

        if (userView != null && userView.isShown() && userView.getTag() != null && userView.getTag().equals(user))
            refreshTalkingState(userView, user);

    }

    private void refreshElements(final View view, final User user) {
        TextView name = (TextView) view.findViewById(R.id.userRowName);

        name.setText(user.getName());
        try {
            name.setTypeface(null, user.getSession() == mService.getSession() ? Typeface.BOLD : Typeface.NORMAL);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        refreshTalkingState(view, user);
    }

    private void refreshTalkingState(final View view, final User user) {
        final ImageView state = (ImageView) view.findViewById(R.id.userRowState);

        if (user.isSelfDeafened())
            state.setImageResource(R.drawable.ic_deafened);
        else if (user.isSelfMuted())
            state.setImageResource(R.drawable.ic_muted);
        else if (user.isDeafened())
            state.setImageResource(R.drawable.ic_server_deafened);
        else if (user.isMuted())
            state.setImageResource(R.drawable.ic_server_muted);
        else if (user.isSuppressed())
            state.setImageResource(R.drawable.ic_suppressed);
        else
            if (user.getTalkState() == User.TalkState.TALKING)
                state.setImageResource(R.drawable.ic_talking_on);
            else
                state.setImageResource(R.drawable.ic_talking_off);
    }

    public void setChannelMenuListener(ChannelMenuListener listener) {
        mMenuListener = listener;
    }

    @Override
    public User getChild(int groupId, int childPosition) {
        Channel channel = mChannels.get(groupId);
        int session = channel.getUsers().get(childPosition);
        return mUsers.get(session);
    }

    @Override
    public View getChildView(int groupId, int childPosition, int depth, View v, ViewGroup arg4) {
        if (v == null) {
            final LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.channel_user_row, arg4, false);
        }

        final User user = getChild(groupId, childPosition);

        if(user != null)
            refreshElements(v, user);
        v.setTag(user);

        // Pad the view depending on channel's nested level.
        final View titleView = v.findViewById(R.id.channel_user_row_title);
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float margin = (depth + 1) * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
        titleView.setPadding((int) margin, titleView.getPaddingTop(), titleView.getPaddingRight(), titleView.getPaddingBottom());

        return v;
    }

    @Override
    public List<Integer> getRootIds() {
        return mRootChannels;
    }

    @Override
    public int getGroupCount(int parentId) {
        if(mShowPinnedOnly) return 0; // No subchannels when using pinned
        Channel parent = mChannels.get(parentId);
        return parent.getSubchannels().size();
    }

    @Override
    public int getChildCount(int arg0) {
        Channel channel = mChannels.get(arg0);
        return channel.getUsers().size();
    }

    @Override
    public Channel getGroup(int arg0) {
        return mChannels.get(arg0);
    }

    @Override
    public View getGroupView(final int groupId, int depth, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            final LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.channel_row, parent, false);
        }

        final Channel channel = mChannels.get(groupId);

        boolean expandUsable = channel.getSubchannels().size() > 0 ||
                               channel.getSubchannelUserCount() > 0;
        ImageView expandView = (ImageView) v.findViewById(R.id.channel_row_expand);
        expandView.setImageResource((isGroupExpanded(groupId) || !expandUsable) ? R.drawable.ic_action_expanded : R.drawable.ic_action_collapsed);
        expandView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(isGroupExpanded(groupId)) collapseGroup(groupId);
                else expandGroup(groupId);
                notifyVisibleSetChanged();
            }
        });
        // Dim channel expand toggle when no subchannels exist
        expandView.setEnabled(expandUsable);
        expandView.setVisibility(expandUsable ? View.VISIBLE : View.INVISIBLE);

        TextView nameView = (TextView) v
                .findViewById(R.id.channel_row_name);
        TextView countView = (TextView) v.findViewById(R.id.channel_row_count);

        nameView.setText(channel.getName());

        int userCount = channel.getSubchannelUserCount();
        countView.setText(String.format("%d", userCount));
        countView.setTextColor(getContext().getResources().getColor(userCount > 0 ? R.color.holo_blue_light : android.R.color.darker_gray));

        ImageView moreView = (ImageView) v.findViewById(R.id.channel_row_more);
        moreView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mMenuListener != null)
                    mMenuListener.showChannelMenu(channel, v);
            }
        });

        View channelTitle = v.findViewById(R.id.channel_row_title);

        // Pad the view depending on channel's nested level.
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float margin = depth * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
        channelTitle.setPadding((int) margin, channelTitle.getPaddingTop(), channelTitle.getPaddingRight(),
                channelTitle.getPaddingBottom());

        return v;
    }

    @Override
    public int getGroupId(int parentId, int groupPosition) {
        Channel parent = mChannels.get(parentId);
        Channel channel = mChannels.get(parent.getSubchannels().get(groupPosition));
        return channel.getId();
    }

    @Override
    public int getChildId(int groupId, int childPosition) {
        Channel channel = mChannels.get(groupId);
        return channel.getUsers().get(childPosition);
    }

    @Override
    public boolean isGroupExpandedByDefault(int groupId) {
        // Return true if there are channels in the chain with more than 0 users.
        return mChannels.get(groupId).getSubchannelUserCount() > 0;
    }
}
