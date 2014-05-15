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

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;

import java.util.List;

/**
 * Created by andrew on 11/05/14.
 */
public class FavouriteServerAdapter extends ServerAdapter<Server> {

    private FavouriteServerAdapterMenuListener mListener;

    public FavouriteServerAdapter(Context context, List<Server> servers, FavouriteServerAdapterMenuListener listener) {
        super(context, R.layout.server_list_row, servers);
        mListener = listener;
    }

    @Override
    public int getPopupMenuResource() {
        return R.menu.popup_favourite_server;
    }

    @Override
    public boolean onPopupItemClick(Server server, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.menu_server_edit:
                mListener.editServer(server);
                return true;
            case R.id.menu_server_share:
                mListener.shareServer(server);
                return true;
            case R.id.menu_server_delete:
                mListener.deleteServer(server);
                return true;
            default:
                return false;
        }
    }

    public static interface FavouriteServerAdapterMenuListener {
        public void editServer(Server server);
        public void shareServer(Server server);
        public void deleteServer(Server server);
    }
}
