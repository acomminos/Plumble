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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.PublicServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
* Created by andrew on 07/05/14.
*/
public class PublicServerAdapter extends ServerAdapter<PublicServer> {
    private List<PublicServer> mUnfilteredServers;
    private PublicServerAdapterMenuListener mListener;

    public PublicServerAdapter(Context context, List<PublicServer> servers, PublicServerAdapterMenuListener listener) {
        super(context, R.layout.public_server_list_row, servers);
        mUnfilteredServers = new ArrayList<PublicServer>(servers);
        mListener = listener;
    }

    public void filter(String queryName, String queryCountry) {
        clear();

        for(PublicServer server : mUnfilteredServers) {
            String serverName = server.getName() != null ? server.getName().toUpperCase(Locale.US) : "";
            String serverCountry = server.getCountry() != null ? server.getCountry().toUpperCase(Locale.US) : "";

            if(serverName.contains(queryName) && serverCountry.contains(queryCountry))
                add(server);
        }
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        View view = super.getView(position, v, parent);

        final PublicServer server = getItem(position);

        TextView locationText = (TextView) view.findViewById(R.id.server_row_location);
        locationText.setText(server.getCountry());

        return view;
    }

    @Override
    public int getPopupMenuResource() {
        return R.menu.popup_public_server;
    }

    @Override
    public boolean onPopupItemClick(Server server, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.menu_server_favourite:
                mListener.favouriteServer(server);
                return true;
            default:
                return false;
        }
    }

    public static interface PublicServerAdapterMenuListener {
        public void favouriteServer(Server server);
    }
}
