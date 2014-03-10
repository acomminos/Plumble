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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.BuildConfig;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.db.PublicServer;

import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a list of servers, and allows the user to connect and edit them.
 * @author morlunk
 *
 */
public class ServerListFragment extends Fragment implements OnItemClickListener {

    public interface ServerConnectHandler {
        public void connectToServer(Server server);
        public void connectToPublicServer(PublicServer server);
    }

	private ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
	private GridView mServerGrid;
	private ServerAdapter mServerAdapter;
	private Map<Server, ServerInfoResponse> mInfoResponses = new HashMap<Server, ServerInfoResponse>();
	
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
		if(item.getItemId() == R.id.menu_add_server_item) {
			addServer();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void addServer() {
		ServerEditFragment infoDialog = new ServerEditFragment();
		infoDialog.show(getFragmentManager(), "serverInfo");
	}
	
	private void editServer(Server server) {
		ServerEditFragment infoDialog = new ServerEditFragment();
		Bundle args = new Bundle();
		args.putParcelable("server", server);
		infoDialog.setArguments(args);
		infoDialog.show(getFragmentManager(), "serverInfo");
	}
	
	private void shareServer(Server server) {
		// Build Mumble server URL
		String serverUrl = "mumble://"+server.getHost()+":"+server.getPort()+"/";
		
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl));
		intent.setType("text/plain");
		startActivity(intent);
	}
	
	private void deleteServer(final Server server) {
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
		mServerAdapter = new ServerAdapter(getActivity(), servers);
		mServerGrid.setAdapter(mServerAdapter);
		
		for(final Server server : servers) {
			new ServerInfoTask() {
				protected void onPostExecute(ServerInfoResponse result) {
					super.onPostExecute(result);
					mInfoResponses.put(server, result);
					mServerAdapter.notifyDataSetChanged();
				};
			}.execute(server);
		}
	}



    public List<Server> getServers() {
        List<Server> servers = mDatabaseProvider.getDatabase().getServers();
        return servers;
    }
	
	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		mConnectHandler.connectToServer(mServerAdapter.getItem(arg2));
	}
	
	private class ServerAdapter extends ArrayAdapter<Server> {

        private boolean mLightTheme;
		
		public ServerAdapter(Context context, List<Server> servers) {
			super(context, android.R.id.text1, servers);
            mLightTheme = R.style.Theme_Plumble == Settings.getInstance(context).getTheme();
		}
		
		@Override
		public long getItemId(int position) {
			return getItem(position).getId();
		}

		@Override
		public final View getView(
			final int position,
			final View v,
			final ViewGroup parent) {
			View view = v;
			
			if(v == null) {
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(R.layout.server_list_row, parent, false);

			}

			final Server server = getItem(position);
			
			ServerInfoResponse infoResponse = mInfoResponses.get(server);
			// If there is a null value for the server info (rather than none at all), the request must have failed.
			boolean requestExists = infoResponse != null;
			boolean requestFailure = infoResponse != null && infoResponse.isDummy();

			TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
			TextView userText = (TextView) view.findViewById(R.id.server_row_user);
			TextView addressText = (TextView) view.findViewById(R.id.server_row_address);
			
			if(server.getName().equals("")) {
				nameText.setText(server.getHost());
			} else {
				nameText.setText(server.getName());
			}
			
			userText.setText(server.getUsername());
			addressText.setText(server.getHost()+":"+server.getPort());
			
			ImageView moreButton = (ImageView) view.findViewById(R.id.server_row_more);
            moreButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popupMenu = new PopupMenu(getContext(), v);
                    MenuInflater inflater = popupMenu.getMenuInflater();
                    inflater.inflate(R.menu.popup_server_row, popupMenu.getMenu());
                    ServerPopupMenuItemClickListener listener = new ServerPopupMenuItemClickListener(server);
                    popupMenu.setOnMenuItemClickListener(listener);
                    popupMenu.show();
                }
            });
			
			TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
            TextView serverLatencyText = (TextView) view.findViewById(R.id.server_row_latency);
			TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
			ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
			
			serverVersionText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverUsersText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
            serverLatencyText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);

			if(infoResponse != null && !requestFailure) {
				serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
				serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
                serverLatencyText.setText(infoResponse.getLatency()+"ms");
			} else if(requestFailure) {
				serverVersionText.setText(R.string.offline);
				serverUsersText.setText("");
                serverLatencyText.setText("");
			}
			
			return view;
		}
	}

    private class ServerPopupMenuItemClickListener implements PopupMenu.OnMenuItemClickListener {
		
		private Server server;
		
		public ServerPopupMenuItemClickListener(Server server) {
			this.server = server;
		}
		
		public boolean onMenuItemClick(android.view.MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menu_edit_item:
				editServer(server);
				return true;
			case R.id.menu_share_item:
				shareServer(server);
				return true;
			case R.id.menu_delete_item:
				deleteServer(server);
				return true;
			}
			return false;
		}
	}
}
