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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.view.CircleDrawable;
import com.morlunk.mumbleclient.view.FlipDrawable;
import com.morlunk.mumbleclient.view.PlumbleNestedAdapter;
import com.morlunk.mumbleclient.view.PlumbleNestedListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrew on 31/07/13.
 */
public class ChannelListAdapter extends PlumbleNestedAdapter<Channel, User> {
    /**
     * Time (in ms) to run the flip animation for.
     */
    private static final long FLIP_DURATION = 350;

    private IJumbleService mService;
    private PlumbleDatabase mDatabase;
    private List<Integer> mRootChannels = new ArrayList<Integer>();

    public ChannelListAdapter(Context context, IJumbleService service, PlumbleDatabase database, boolean showPinnedOnly) throws RemoteException {
        super(context);
        mService = service;
        mDatabase = database;

        mRootChannels = new ArrayList<Integer>();
        if(showPinnedOnly) {
            mRootChannels = mDatabase.getPinnedChannels(mService.getConnectedServer().getId());
        } else {
            mRootChannels.add(0);
        }
    }

    @Override
    public User getChild(int groupId, int childPosition) {
        try {
            Channel channel = mService.getChannel(groupId);
            if (channel == null) {
                return null;
            }
            int session = channel.getUsers().get(childPosition);
            return mService.getUser(session);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public View getChildView(int groupId, int childPosition, int depth, View v, ViewGroup arg4) {
        UserViewHolder uvh;
        if (v == null) {
            final LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.channel_user_row, arg4, false);

            uvh = new UserViewHolder();
            uvh.mUserHolder = (LinearLayout) v.findViewById(R.id.user_row_title);
            uvh.mUserTalkHighlight = (ImageView) v.findViewById(R.id.user_row_talk_highlight);
            uvh.mUserName = (TextView) v.findViewById(R.id.user_row_name);
            v.setTag(uvh);
        } else {
            uvh = (UserViewHolder) v.getTag();
        }

        final User user = getChild(groupId, childPosition);

        uvh.mUserName.setText(user.getName());
        try {
            uvh.mUserName.setTypeface(null, user.getSession() == mService.getSession() ? Typeface.BOLD : Typeface.NORMAL);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        uvh.mUserTalkHighlight.setImageDrawable(getTalkStateDrawable(user));

        // Pad the view depending on channel's nested level.ed
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float margin = (depth + 1) * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
        uvh.mUserHolder.setPadding((int) margin,
                uvh.mUserHolder.getPaddingTop(),
                uvh.mUserHolder.getPaddingRight(),
                uvh.mUserHolder.getPaddingBottom());

        return v;
    }

    /**
     * Looks for the user's entry in the list and updates it with a fancy animation.
     * @param user The user to update state of.
     * @param listView The list view containing the entries.
     */
    public void animateUserStateChange(User user, PlumbleNestedListView listView) {
        int position = getVisibleFlatChildPosition(user.getSession());
        if (position < 0) {
            return;
        }

        if (listView.getFirstVisiblePosition() > position ||
                listView.getLastVisiblePosition() < position) {
            return; // Ignore animating views out of sight
        }

        View v = listView.getChildAt(position - listView.getFirstVisiblePosition());

        if (v == null || v.getTag() == null || !(v.getTag() instanceof UserViewHolder)) {
            return;
        }

        UserViewHolder uvh = (UserViewHolder) v.getTag();

        if (uvh.mUserTalkHighlight.getDrawable() == null) {
            return;
        }

        Drawable newState = getTalkStateDrawable(user);
        Drawable oldState = uvh.mUserTalkHighlight.getDrawable().getCurrent();

        if (!newState.getConstantState().equals(oldState.getConstantState())) {
            if (Build.VERSION.SDK_INT >= 12) {
                // "Flip" in new talking state.
                FlipDrawable drawable = new FlipDrawable(oldState, newState);
                uvh.mUserTalkHighlight.setImageDrawable(drawable);
                drawable.start(FLIP_DURATION);
            } else {
                // If we're on a platform without ValueAnimator, simply set the state image.
                uvh.mUserTalkHighlight.setImageDrawable(newState);
            }
        }
    }

    private Drawable getTalkStateDrawable(User user) {
        Resources resources = getContext().getResources();
        if (user.isSelfDeafened()) {
            return resources.getDrawable(R.drawable.outline_circle_deafened);
        } else if (user.isDeafened()) {
            return resources.getDrawable(R.drawable.outline_circle_server_deafened);
        } else if (user.isSelfMuted()) {
            return resources.getDrawable(R.drawable.outline_circle_muted);
        } else if (user.isMuted()) {
            return resources.getDrawable(R.drawable.outline_circle_server_muted);
        } else if (user.isSuppressed()) {
            return resources.getDrawable(R.drawable.outline_circle_suppressed);
        } else if (user.getTalkState() == User.TalkState.TALKING ||
                user.getTalkState() == User.TalkState.SHOUTING ||
                user.getTalkState() == User.TalkState.WHISPERING) {
            // TODO: add whisper and shouting resources
            return resources.getDrawable(R.drawable.outline_circle_talking_on);
        } else {
            // Passive drawables
//            if (user.getTexture() != null) {
//                return new CircleDrawable(getContext().getResources(), user.getTexture());
//            } else {
                return resources.getDrawable(R.drawable.outline_circle_talking_off);
//            }
        }
    }

    @Override
    public List<Integer> getRootIds() {
        return mRootChannels;
    }

    @Override
    public int getGroupCount(int parentId) {
        Channel parent = getGroup(parentId);
        return parent.getSubchannels().size();
    }

    @Override
    public int getChildCount(int arg0) {
        Channel channel = getGroup(arg0);
        return channel.getUsers().size();
    }

    @Override
    public Channel getGroup(int arg0) {
        try {
            return mService.getChannel(arg0);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public View getGroupView(final int groupId, int depth, View convertView, ViewGroup parent) {
        View v = convertView;
        ChannelViewHolder cvh;
        if (v == null) {
            final LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.channel_row, parent, false);
            cvh = new ChannelViewHolder();
            cvh.mChannelHolder = (LinearLayout) v.findViewById(R.id.channel_row_title);
            cvh.mChannelExpandToggle = (ImageView) v.findViewById(R.id.channel_row_expand);
            cvh.mChannelName = (TextView) v.findViewById(R.id.channel_row_name);
            cvh.mChannelUserCount = (TextView) v.findViewById(R.id.channel_row_count);
            v.setTag(cvh);
        } else {
            cvh = (ChannelViewHolder) v.getTag();
        }

        final Channel channel = getGroup(groupId);

        boolean expandUsable = channel.getSubchannels().size() > 0 ||
                               channel.getSubchannelUserCount() > 0;
        cvh.mChannelExpandToggle.setImageResource((isGroupExpanded(groupId) || !expandUsable) ? R.drawable.ic_action_expanded : R.drawable.ic_action_collapsed);
        cvh.mChannelExpandToggle.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isGroupExpanded(groupId)) collapseGroup(groupId);
                else expandGroup(groupId);
                notifyVisibleSetChanged();
            }
        });
        // Dim channel expand toggle when no subchannels exist
        cvh.mChannelExpandToggle.setEnabled(expandUsable);
        cvh.mChannelExpandToggle.setVisibility(expandUsable ? View.VISIBLE : View.INVISIBLE);

        cvh.mChannelName.setText(channel.getName());

        int userCount = channel.getSubchannelUserCount();
        cvh.mChannelUserCount.setText(String.format("%d", userCount));

        // Pad the view depending on channel's nested level.
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float margin = depth * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
        cvh.mChannelHolder.setPadding((int) margin,
                cvh.mChannelHolder.getPaddingTop(),
                cvh.mChannelHolder.getPaddingRight(),
                cvh.mChannelHolder.getPaddingBottom());

        return v;
    }

    @Override
    public int getGroupId(int parentId, int groupPosition) {
        Channel parent = getGroup(parentId);
        Channel channel = getGroup(parent.getSubchannels().get(groupPosition));
        return channel.getId();
    }

    @Override
    public int getChildId(int groupId, int childPosition) {
        Channel channel = getGroup(groupId);
        return channel.getUsers().get(childPosition);
    }

    @Override
    public boolean isGroupExpandedByDefault(int groupId) {
        // Return true if there are channels in the chain with more than 0 users.
        return getGroup(groupId).getSubchannelUserCount() > 0;
    }

    private static class UserViewHolder {
        public LinearLayout mUserHolder;
        public TextView mUserName;
//        public ImageView mUserAvatar;
        public ImageView mUserTalkHighlight;
    }

    private static class ChannelViewHolder {
        public LinearLayout mChannelHolder;
        public ImageView mChannelExpandToggle;
        public TextView mChannelName;
        public TextView mChannelUserCount;
    }
}
