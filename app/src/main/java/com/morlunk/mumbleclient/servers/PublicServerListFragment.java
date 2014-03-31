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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Xml;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.morlunk.mumbleclient.Constants;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.db.PublicServer;
import com.morlunk.mumbleclient.util.CardDrawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays a list of public servers that can be connected to, sorted, and favourited.
 * @author morlunk
 *
 */
public class PublicServerListFragment extends Fragment implements OnItemClickListener {
    
    public static final int MAX_ACTIVE_PINGS = 50;
    
    private ServerListFragment.ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private List<PublicServer> mServers;
    private GridView mServerGrid;
    private ProgressBar mServerProgress;
    private PublicServerAdapter mServerAdapter;
    private int mActivePingCount = 0;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setHasOptionsMenu(true);
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        try {
            mConnectHandler = (ServerListFragment.ServerConnectHandler)activity;
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
    
    public void setServers(List<PublicServer> servers) {
        mServers = servers;
        mServerProgress.setVisibility(View.GONE);
        mServerAdapter = new PublicServerAdapter(getActivity(), servers);
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
    
    private class PublicServerAdapter extends ArrayAdapter<PublicServer> {
        private Map<PublicServer, ServerInfoResponse> infoResponses = new HashMap<PublicServer, ServerInfoResponse>();
        private List<PublicServer> originalServers;
        
        public PublicServerAdapter(Context context, List<PublicServer> servers) {
            super(context, android.R.id.text1, servers);
            originalServers = new ArrayList<PublicServer>(servers);
        }
        
        public void filter(String queryName, String queryCountry) {
            clear();
            
            for(PublicServer server : originalServers) {
                String serverName = server.getName() != null ? server.getName().toUpperCase(Locale.US) : "";
                String serverCountry = server.getCountry() != null ? server.getCountry().toUpperCase(Locale.US) : "";
                
                if(serverName.contains(queryName) && serverCountry.contains(queryCountry))
                    add(server);
            }
        }

        @SuppressLint("NewApi")
        @Override
        public final View getView(
            final int position,
            final View v,
            final ViewGroup parent) {
            View view = v;
            
            if(v == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.public_server_list_row, parent, false);
                view.setBackgroundDrawable(CardDrawable.getDrawable(getContext()));
            }
            
            final PublicServer server = getItem(position);
            view.setTag(server);
            
            TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
            TextView addressText = (TextView) view.findViewById(R.id.server_row_address);
            
            if(server.getName().equals("")) {
                nameText.setText(server.getHost());
            } else {
                nameText.setText(server.getName());
            }
            
            addressText.setText(server.getHost()+":"+server.getPort());
            
            TextView locationText = (TextView) view.findViewById(R.id.server_row_location);
            locationText.setText(server.getCountry());
            
            // Ping server if available
            if(!infoResponses.containsKey(server) && mActivePingCount < MAX_ACTIVE_PINGS) {
                mActivePingCount++;
                final View serverView = view; // Create final instance of view for use in asynctask
                ServerInfoTask task = new ServerInfoTask() {
                    protected void onPostExecute(ServerInfoResponse result) {
                        super.onPostExecute(result);
                        infoResponses.put(server, result);
                        if(serverView != null && serverView.isShown() && serverView.getTag() == server)
                            updateInfoResponseView(serverView, server);
                        mActivePingCount--;
                        Log.d(Constants.TAG, "DEBUG: Servers remaining in queue: "+ mActivePingCount);
                    };
                };
                
                // Execute on parallel threads if API >= 11. RACE CAR THREADING, WOOOOOOOOOOOOOOOOOOOOOO
                if(VERSION.SDK_INT >= 11)
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, server);
                else
                    task.execute(server);
            }
            
            ImageView favoriteButton = (ImageView)view.findViewById(R.id.server_row_favorite);
            
            favoriteButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

                    Settings settings = Settings.getInstance(getActivity());

                    // Allow username entry
                    final EditText usernameField = new EditText(getContext());
                    usernameField.setHint(settings.getDefaultUsername());
                    alertBuilder.setView(usernameField);

                    alertBuilder.setTitle(R.string.addFavorite);
                    
                    alertBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String username = usernameField.getText().toString();
                            if(username.equals(""))
                                server.setUsername(usernameField.getHint().toString());

                            mDatabaseProvider.getDatabase().addServer(server);
                        }
                    });
                    
                    alertBuilder.show();
                }
                
            });
            
            updateInfoResponseView(view, server);
            
            return view;
        }
        
        private void updateInfoResponseView(View view, PublicServer server) {
            ServerInfoResponse infoResponse = infoResponses.get(server);
            // If there is a null value for the server info (rather than none at all), the request must have failed.
            boolean requestExists = infoResponse != null;
            boolean requestFailure = infoResponse != null && infoResponse.isDummy();
            
            TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
            TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
            ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
            
            serverVersionText.setVisibility(!requestFailure && !requestExists ? View.INVISIBLE : View.VISIBLE);
            serverUsersText.setVisibility(!requestFailure && !requestExists ? View.INVISIBLE : View.VISIBLE);
            serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);
            
            if(infoResponse != null && !requestFailure) {
                serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
                serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
            } else if(requestFailure) {
                serverVersionText.setText(R.string.offline);
                serverUsersText.setText("");
            } else {
                serverVersionText.setText(R.string.noServerInfo);
            }
        }
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

    private class PublicServerFetchTask extends AsyncTask<Void, Void, List<PublicServer>> {

    private static final String MUMBLE_PUBLIC_URL = "http://www.mumble.info/list2.cgi";

    @Override
    protected List<PublicServer> doInBackground(Void... params) {
        try {
            // Fetch XML from server
            URL url = new URL(MUMBLE_PUBLIC_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.addRequestProperty("version", com.morlunk.jumble.Constants.PROTOCOL_STRING);
            connection.connect();
            InputStream stream = connection.getInputStream();

            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(stream, "UTF-8");
            parser.nextTag();

            List<PublicServer> serverList = new ArrayList<PublicServer>();

            parser.require(XmlPullParser.START_TAG, null, "servers");
            while(parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                serverList.add(readEntry(parser));
            }
            parser.require(XmlPullParser.END_TAG, null, "servers");

            return serverList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private PublicServer readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(null, "name");
        String ca = parser.getAttributeValue(null, "ca");
        String continentCode = parser.getAttributeValue(null, "continent_code");
        String country = parser.getAttributeValue(null, "country");
        String countryCode = parser.getAttributeValue(null, "country_code");
        String ip = parser.getAttributeValue(null, "ip");
        String port = parser.getAttributeValue(null, "port");
        String region = parser.getAttributeValue(null, "region");
        String url = parser.getAttributeValue(null, "url");

        parser.nextTag();

        PublicServer server = new PublicServer(name, ca, continentCode, country, countryCode, ip, Integer.parseInt(port), region, url);

        return server;
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
            if(country != null)
                servers = Collections2.filter(mServers, new Predicate<PublicServer>() {
                    @Override
                    public boolean apply(PublicServer publicServer) {
                        return country.equals(publicServer.getCountryCode());
                    }
                });
            else
                servers = mServers;

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
