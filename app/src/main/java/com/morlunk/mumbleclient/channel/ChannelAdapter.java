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
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.TalkState;
import com.morlunk.jumble.model.User;
import com.morlunk.mumbleclient.R;

/**
 * Simple adapter to display the users in a single channel.
 * Created by andrew on 24/11/13.
 */
public class ChannelAdapter extends BaseAdapter {

    private Context mContext;
    private IChannel mChannel;

    public ChannelAdapter(Context context, IChannel channel) {
        mContext = context;
        mChannel = channel;
    }

    @Override
    public int getCount() {
        return mChannel.getUsers().size();
    }

    @Override
    public Object getItem(int position) {
        return mChannel.getUsers().get(position);
    }

    @Override
    public long getItemId(int position) {
        IUser user = mChannel.getUsers().get(position);
        if (user != null)
            return user.getUserId();
        return -1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if(v == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            v = layoutInflater.inflate(R.layout.overlay_user_row, parent, false);
        }
        User user = (User) getItem(position);
        TextView titleView = (TextView) v.findViewById(R.id.user_row_name);
        titleView.setText(user.getName());

        ImageView state = (ImageView) v.findViewById(R.id.user_row_state);
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
        if (user.getTalkState() == TalkState.TALKING)
            state.setImageResource(R.drawable.ic_talking_on);
        else
            state.setImageResource(R.drawable.ic_talking_off);

        return v;
    }

    public void setChannel(IChannel channel) {
        mChannel = channel;
        notifyDataSetChanged();
    }

    public IChannel getChannel() {
        return mChannel;
    }
}
