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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.db.PublicServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays a list of public servers that can be connected to, sorted, and favourited.
 * @author morlunk
 *
 */
public class PublicServerListFragment extends Fragment implements OnItemClickListener, PublicServerAdapter.PublicServerAdapterMenuListener {
    
    private FavouriteServerListFragment.ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private List<PublicServer> mServers;
    private GridView mServerGrid;
    private ProgressBar mServerProgress;
    private PublicServerAdapter mServerAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setHasOptionsMenu(true);
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        try {
            mConnectHandler = (FavouriteServerListFragment.ServerConnectHandler)activity;
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()+" must implement ServerConnectHandler and DatabaseProvider!");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fillPublicList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_public_server_list, container, false);
        mServerGrid = (GridView) view.findViewById(R.id.server_list_grid);
        mServerGrid.setOnItemClickListener(this);
        if(mServerAdapter != null)
            mServerGrid.setAdapter(mServerAdapter);
        mServerProgress = (ProgressBar) view.findViewById(R.id.serverProgress);
        mServerProgress.setVisibility(mServerAdapter == null ? View.VISIBLE : View.GONE);
        return view;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_match_server).setVisible(VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB); // Executors only supported on Honeycomb +
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_public_server_list, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isFilled()) {
            switch(item.getItemId()) {
                case R.id.menu_match_server:
                    showMatchDialog();
                    break;
                case R.id.menu_sort_server_item:
                    showSortDialog();
                    return true;
                case R.id.menu_search_server_item:
                    showFilterDialog();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void favouriteServer(final Server server) {
        final Settings settings = Settings.getInstance(getActivity());
        final EditText usernameField = new EditText(getActivity());
        usernameField.setHint(settings.getDefaultUsername());
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        adb.setTitle(R.string.addFavorite);
        adb.setView(usernameField);
        adb.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(usernameField.getText().length() > 0) {
                    server.setUsername(usernameField.getText().toString());
                } else {
                    server.setUsername(settings.getDefaultUsername());
                }
                PlumbleDatabase database = mDatabaseProvider.getDatabase();
                database.addServer(server);
            }
        });
        adb.setNegativeButton(android.R.string.cancel, null);
        adb.show();
    }
    
    public void setServers(List<PublicServer> servers) {
        mServers = servers;
        mServerProgress.setVisibility(View.GONE);
        mServerAdapter = new PublicServerAdapter(getActivity(), servers, this);
        mServerGrid.setAdapter(mServerAdapter);
    }
    
    public boolean isFilled() {
        return mServerAdapter != null;
    }

    private void showMatchDialog() {
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        adb.setTitle(R.string.server_match);
        adb.setMessage(R.string.server_match_description);
        adb.setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                findOptimalServer();
            }
        });
        adb.setNegativeButton(android.R.string.cancel, null);
        adb.show();
    }

    private void findOptimalServer() {
        MatchServerTask matchServerTask = new MatchServerTask();
        matchServerTask.execute(Locale.getDefault().getCountry());
    }

    private void showSortDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
        alertBuilder.setTitle(R.string.sortBy);
        alertBuilder.setItems(new String[] { getString(R.string.name), getString(R.string.country)}, new SortClickListener());
        alertBuilder.show();
    }
    
    private void showFilterDialog() {
        View dialogView = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.dialog_server_search, null);
        final EditText nameText = (EditText) dialogView.findViewById(R.id.server_search_name);
        final EditText countryText = (EditText) dialogView.findViewById(R.id.server_search_country);
                
        final AlertDialog dlg = new AlertDialog.Builder(getActivity()).
            setTitle(R.string.search).
            setView(dialogView).
            setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which)
                {
                    String queryName = nameText.getText().toString().toUpperCase(Locale.US);
                    String queryCountry = countryText.getText().toString().toUpperCase(Locale.US);
                    mServerAdapter.filter(queryName, queryCountry);
                    dialog.dismiss();
                }
            }).create();
        
        nameText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        nameText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event)
            {
                String queryName = nameText.getText().toString().toUpperCase(Locale.US);
                String queryCountry = countryText.getText().toString().toUpperCase(Locale.US);
                mServerAdapter.filter(queryName, queryCountry);
                dlg.dismiss();
                return true;
            }
        });
        
        countryText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        countryText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event)
            {
                String queryName = nameText.getText().toString().toUpperCase(Locale.US);
                String queryCountry = countryText.getText().toString().toUpperCase(Locale.US);
                mServerAdapter.filter(queryName, queryCountry);
                dlg.dismiss();
                return true;
            }
        });
        
        // Show keyboard automatically
        nameText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
        
        dlg.show();
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        mConnectHandler.connectToPublicServer(mServerAdapter.getItem(arg2));
    }

    private void fillPublicList() {
        new PublicServerFetchTask() {
            protected void onPostExecute(List<PublicServer> result) {
                super.onPostExecute(result);

                if (result == null) {
                    // Handle error
                    Toast.makeText(getActivity(), R.string.error_fetching_servers, Toast.LENGTH_SHORT).show();
                    return;
                }

                if(isVisible()) // Prevents NPEs when fragment is detached.
                    setServers(result);
            };
        }.execute();
    }

    private class SortClickListener implements DialogInterface.OnClickListener {

        private static final int SORT_NAME = 0;
        private static final int SORT_COUNTRY = 1;
        
        private Comparator<PublicServer> nameComparator = new Comparator<PublicServer>() {
            @Override
            public int compare(PublicServer lhs, PublicServer rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        };

        private Comparator<PublicServer> countryComparator = new Comparator<PublicServer>() {
            @Override
            public int compare(PublicServer lhs, PublicServer rhs) {
                if(rhs.getCountry() == null) return -1;
                else if(lhs.getCountry() == null) return 1;
                return lhs.getCountry().compareTo(rhs.getCountry());
            }
        };
        
        
        @Override
        public void onClick(DialogInterface dialog, int which) {
            ArrayAdapter<PublicServer> arrayAdapter = mServerAdapter;
            if(which == SORT_NAME) {
                arrayAdapter.sort(nameComparator);
            } else if(which == SORT_COUNTRY) {
                arrayAdapter.sort(countryComparator);
            }
        }
    }

    /**
     * Finds an empty server in the user's country code with low latency.
     * By default, it will show a ProgressDialog while it performs this and an AlertDialog allowing the user to connect.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private class MatchServerTask extends AsyncTask<String, Void, ServerInfoResponse> {

        /**
         * Query until we find up to SEARCH_RANGE empty servers that meet the zero user and location requirements.
         * Once we have that, then we'll sort them based on latency.
         * TODO make this a user-changeable option.
         */
        public static final int SEARCH_RANGE = 20;

        private Comparator<ServerInfoResponse> mLatencyComparator = new Comparator<ServerInfoResponse>() {
            @Override
            public int compare(ServerInfoResponse lhs, ServerInfoResponse rhs) {
                return lhs.getLatency() == rhs.getLatency() ? 0 :
                        lhs.getLatency() < rhs.getLatency() ? -1 : 1;
            }
        };

        private class MatchServerInfoTask extends ServerInfoTask {
            @Override
            protected void onPostExecute(ServerInfoResponse serverInfoResponse) {
                mResponseCount++;
                if(serverInfoResponse == null) {
                    // TODO handle bad responses
                } else if(serverInfoResponse.getCurrentUsers() == 0 &&
                        serverInfoResponse.getVersion() == com.morlunk.jumble.Constants.PROTOCOL_VERSION) {
                    mGoodResponses.add(serverInfoResponse);
                }

                // Once we have a good sample of results, shut down the ping threads.
                if(mResponseCount >= mResponsesToSend) {
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    mPingExecutor.shutdownNow();
                }
            }

        }

        private ExecutorService mPingExecutor = Executors.newFixedThreadPool(5); // Run 5 concurrent pings at a time
        private Object mLock = new Object();

        private List<ServerInfoResponse> mGoodResponses = Collections.synchronizedList(new ArrayList<ServerInfoResponse>());
        private volatile int mResponseCount = 0;
        private int mResponsesToSend = SEARCH_RANGE;

        private ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = ProgressDialog.show(getActivity(), null, getString(R.string.server_match_progress));
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
        }

        @Override
        protected ServerInfoResponse doInBackground(String... params) {
            final String country = params.length > 0 ? params[0] : null; // If a country is provided, search within country

            Collection<PublicServer> servers;
            if(country != null) {
                servers = new LinkedList<PublicServer>();
                for (PublicServer server : mServers) {
                    if (country.equals(server.getCountryCode())) {
                        servers.add(server);
                    }
                }
            } else {
                servers = mServers;
            }

            // For countries with 0 servers, immediately return null.
            if(servers.size() == 0)
                return null;

            // If there are less servers than the value of our range, deal with it.
            mResponsesToSend = Math.min(SEARCH_RANGE, servers.size());

            Iterator iterator = servers.iterator();
            while(iterator.hasNext() && mGoodResponses.size() < mResponsesToSend) {
                PublicServer server = (PublicServer) iterator.next();
                new MatchServerInfoTask().executeOnExecutor(mPingExecutor, server);
            }
            try {
                synchronized (mLock) {
                    mLock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Collections.sort(mGoodResponses, mLatencyComparator);

            if(mGoodResponses.size() > 0)
                return mGoodResponses.get(0);
            else
                return null;
        }

        @Override
        protected void onPostExecute(ServerInfoResponse response) {
            super.onPostExecute(response);
            final PublicServer publicServer = response == null ? null : (PublicServer) response.getServer();
            mProgressDialog.hide();

            AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
            if(publicServer != null) {
                adb.setTitle(R.string.server_match_found);
                adb.setMessage(getString(R.string.server_match_info,
                        publicServer.getName(),
                        publicServer.getHost(),
                        publicServer.getPort(),
                        response.getCurrentUsers(),
                        response.getMaximumUsers(),
                        response.getVersionString(),
                        publicServer.getCountry(),
                        response.getLatency()));
                adb.setPositiveButton(R.string.connect, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mConnectHandler.connectToPublicServer(publicServer);
                    }
                });
            } else {
                adb.setTitle(R.string.server_match_not_found);
                adb.setMessage(R.string.server_match_expand_country);
                adb.setPositiveButton(R.string.expand, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MatchServerTask matchTask = new MatchServerTask();
                        matchTask.execute();
                    }
                });
            }
            adb.setNegativeButton(android.R.string.cancel, null);
            adb.show();
        }
    }
}
