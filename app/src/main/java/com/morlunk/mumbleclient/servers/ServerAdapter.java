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

package com.morlunk.mumbleclient.servers;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by andrew on 05/05/14.
 */
public abstract class ServerAdapter<E extends Server> extends ArrayAdapter<E> {
    private static final int MAX_ACTIVE_PINGS = 50;

    private ConcurrentHashMap<Server, ServerInfoResponse> mInfoResponses = new ConcurrentHashMap<Server, ServerInfoResponse>();
    private ExecutorService mPingExecutor = Executors.newFixedThreadPool(MAX_ACTIVE_PINGS);
    private int mViewResource;

    public ServerAdapter(Context context, int viewResource, List<E> servers) {
        super(context, 0, servers);
        mViewResource = viewResource;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        View view = v;

        if(v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(mViewResource, parent, false);
        }

        final E server = getItem(position);

        ServerInfoResponse infoResponse = mInfoResponses.get(server);
        // If there is a null value for the server info (rather than none at all), the request must have failed.
        boolean requestExists = infoResponse != null;
        boolean requestFailure = infoResponse != null && infoResponse.isDummy();

        TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
        TextView userText = (TextView) view.findViewById(R.id.server_row_user);
        TextView addressText = (TextView) view.findViewById(R.id.server_row_address);

        nameText.setText(server.getName());

        if(userText != null) userText.setText(server.getUsername());
        if(addressText != null) addressText.setText(server.getHost()+":"+server.getPort());

        final ImageView moreButton = (ImageView) view.findViewById(R.id.server_row_more);
        if(moreButton != null) {
            moreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onServerOptionsClick(server, moreButton);
                }
            });
        }

        TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
        TextView serverLatencyText = (TextView) view.findViewById(R.id.server_row_latency);
        TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
        ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);

        serverVersionText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
        serverUsersText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
        serverLatencyText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
        serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);

        if(infoResponse != null && !requestFailure) {
            serverVersionText.setText(getContext().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
            serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
            serverLatencyText.setText(infoResponse.getLatency()+"ms");
        } else if(requestFailure) {
            serverVersionText.setText(R.string.offline);
            serverUsersText.setText("");
            serverLatencyText.setText("");
        }

        // Ping server if available
        if(infoResponse == null) {
            ServerInfoTask task = new ServerInfoTask() {
                protected void onPostExecute(ServerInfoResponse result) {
                    super.onPostExecute(result);
                    mInfoResponses.put(server, result);
                    notifyDataSetChanged();
                }
            };

            // Execute on parallel threads if API >= 11.
            if(Build.VERSION.SDK_INT >= 11) {
                task.executeOnExecutor(mPingExecutor, server);
            } else {
                task.execute(server);
            }
        }

        return view;
    }

    private void onServerOptionsClick(final Server server, View optionsButton) {
        PopupMenu popupMenu = new PopupMenu(getContext(), optionsButton);
        popupMenu.inflate(getPopupMenuResource());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                return onPopupItemClick(server, menuItem);
            }
        });
        popupMenu.show();
    }

    public abstract int getPopupMenuResource();
    public abstract boolean onPopupItemClick(Server server, MenuItem menuItem);
}
