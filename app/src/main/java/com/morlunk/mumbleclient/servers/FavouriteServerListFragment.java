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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.BuildConfig;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.db.PublicServer;

import java.util.List;

/**
 * Displays a list of servers, and allows the user to connect and edit them.
 * @author morlunk
 *
 */
public class FavouriteServerListFragment extends Fragment implements OnItemClickListener, FavouriteServerAdapter.FavouriteServerAdapterMenuListener {

    private ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private GridView mServerGrid;
    private ServerAdapter mServerAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mConnectHandler = (ServerConnectHandler)activity;
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()+" must implement ServerConnectHandler!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_list, container, false);
        mServerGrid = (GridView) view.findViewById(R.id.server_list_grid);
        mServerGrid.setOnItemClickListener(this);
        mServerGrid.setEmptyView(view.findViewById(R.id.server_list_grid_empty));

        TextView donateText = (TextView) view.findViewById(R.id.donate_box);
        donateText.setVisibility(BuildConfig.DONATE_NAG ? View.VISIBLE : View.GONE);
        donateText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent playIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.morlunk.mumbleclient"));
                startActivity(playIntent);
            }
        });

        registerForContextMenu(mServerGrid);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_server_list, menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateServers();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_server_item:
                addServer();
                return true;
            case R.id.menu_quick_connect:
                ServerEditFragment.createServerEditDialog(getActivity(), null, ServerEditFragment.Action.CONNECT_ACTION, true)
                        .show(getFragmentManager(), "serverInfo");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void addServer() {
        ServerEditFragment.createServerEditDialog(getActivity(), null, ServerEditFragment.Action.ADD_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void editServer(Server server) {
        ServerEditFragment.createServerEditDialog(getActivity(), server, ServerEditFragment.Action.EDIT_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void shareServer(Server server) {
        // Build Mumble server URL
        String serverUrl = "mumble://"+server.getHost()+":"+server.getPort()+"/";

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl));
        intent.setType("text/plain");
        startActivity(intent);
    }

    public void deleteServer(final Server server) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
        alertBuilder.setMessage(R.string.confirm_delete_server);
        alertBuilder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mDatabaseProvider.getDatabase().removeServer(server);
                mServerAdapter.remove(server);
            }
        });
        alertBuilder.setNegativeButton(android.R.string.cancel, null);
        alertBuilder.show();
    }

    public void updateServers() {
        List<Server> servers = getServers();
        mServerAdapter = new FavouriteServerAdapter(getActivity(), servers, this);
        mServerGrid.setAdapter(mServerAdapter);
    }



    public List<Server> getServers() {
        List<Server> servers = mDatabaseProvider.getDatabase().getServers();
        return servers;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        mConnectHandler.connectToServer((Server) mServerAdapter.getItem(arg2));
    }

    public static interface ServerConnectHandler {
        public void connectToServer(Server server);
        public void connectToPublicServer(PublicServer server);
    }
}
