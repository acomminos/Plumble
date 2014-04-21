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

package com.morlunk.mumbleclient.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.util.MumbleURLParser;
import com.morlunk.jumble.util.ParcelableByteArray;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.channel.AccessTokenFragment;
import com.morlunk.mumbleclient.channel.ChannelFragment;
import com.morlunk.mumbleclient.channel.ServerInfoFragment;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.db.PlumbleDatabase;
import com.morlunk.mumbleclient.db.PlumbleSQLiteDatabase;
import com.morlunk.mumbleclient.db.PublicServer;
import com.morlunk.mumbleclient.preference.PlumbleCertificateGenerateTask;
import com.morlunk.mumbleclient.preference.Preferences;
import com.morlunk.mumbleclient.servers.PublicServerListFragment;
import com.morlunk.mumbleclient.servers.ServerEditFragment;
import com.morlunk.mumbleclient.servers.ServerListFragment;
import com.morlunk.mumbleclient.service.PlumbleService;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;
import com.morlunk.mumbleclient.util.JumbleServiceProvider;
import com.morlunk.mumbleclient.util.PlumbleTrustStore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

public class PlumbleActivity extends ActionBarActivity implements ListView.OnItemClickListener, ServerListFragment.ServerConnectHandler, JumbleServiceProvider, DatabaseProvider, SharedPreferences.OnSharedPreferenceChangeListener, DrawerAdapter.DrawerDataProvider, ServerEditFragment.ServerEditListener {

    /** Broadcasted when the activity gains focus. Used to dismiss chat notifications, bit of a hack. */
    public static final String ACTION_PLUMBLE_SHOWN = "com.morlunk.mumbleclient.ACTION_PLUMBLE_SHOWN";
    public static final int RECONNECT_DELAY = 10000;

    private static final String SAVED_FRAGMENT_TAG = "fragment";

    private IJumbleService mService;
    private PlumbleDatabase mDatabase;
    private Settings mSettings;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter;
    private int mActiveFragment;

    private ProgressDialog mConnectingDialog;
    private AlertDialog mErrorDialog;
    private AlertDialog.Builder mDisconnectPromptBuilder;

    /** List of fragments to be notified about service state changes. */
    private List<JumbleServiceFragment> mServiceFragments = new ArrayList<JumbleServiceFragment>();

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = (IJumbleService) service;
            try {
                mService.registerObserver(mObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mDrawerAdapter.notifyDataSetChanged();

            for(JumbleServiceFragment fragment : mServiceFragments)
                fragment.setServiceBound(true);

            // Re-show server list if we're showing a fragment that depends on the service.
            try {
                if(getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof JumbleServiceFragment &&
                        !mService.isConnected()) {
                    loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            for(JumbleServiceFragment fragment : mServiceFragments)
                fragment.setServiceBound(false);
        }
    };

    private JumbleObserver mObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            loadDrawerFragment(DrawerAdapter.ITEM_SERVER);
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();

            mConnectingDialog.dismiss();
            if(mErrorDialog != null) mErrorDialog.dismiss();
        }

        @Override
        public void onDisconnected() throws RemoteException {
            // Re-show server list if we're showing a fragment that depends on the service.
            if(getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof JumbleServiceFragment) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
            }
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();

            mConnectingDialog.dismiss();
        }

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            if(mErrorDialog != null) mErrorDialog.dismiss();
            mConnectingDialog.dismiss();

            AlertDialog.Builder ab = new AlertDialog.Builder(PlumbleActivity.this);
            ab.setTitle(R.string.connectionRefused);
            if(!reconnecting) {
                ab.setMessage(message);
                ab.setPositiveButton(android.R.string.ok, null);
            } else {
                ab.setTitle(R.string.connectionRefused);
                ab.setMessage(message+"\n"+getString(R.string.reconnecting, RECONNECT_DELAY/1000));
                ab.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(getService() != null) {
                            try {
                                getService().cancelReconnect();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            mErrorDialog = ab.show();
        }

        @Override
        public void onTLSHandshakeFailed(ParcelableByteArray cert) throws RemoteException {
            byte[] certBytes = cert.getBytes();
            final Server lastServer = getService().getConnectedServer();

            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                final Certificate x509 = certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

                AlertDialog.Builder adb = new AlertDialog.Builder(PlumbleActivity.this);
                adb.setTitle(R.string.untrusted_certificate);
                TextView certView = new TextView(PlumbleActivity.this);
                certView.setText(x509.toString());
                certView.setTypeface(Typeface.MONOSPACE);
                certView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                certView.setHorizontallyScrolling(true);
                certView.setMovementMethod(new ScrollingMovementMethod());
                certView.setHorizontalScrollBarEnabled(true);
                adb.setView(certView);
                adb.setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Try to add to trust store
                        try {
                            String alias = lastServer.getHost(); // FIXME unreliable
                            KeyStore trustStore = PlumbleTrustStore.getTrustStore(PlumbleActivity.this);
                            trustStore.setCertificateEntry(alias, x509);
                            PlumbleTrustStore.saveTrustStore(PlumbleActivity.this, trustStore);
                            Toast.makeText(PlumbleActivity.this, R.string.trust_added, Toast.LENGTH_LONG).show();
                            connectToServer(lastServer); // FIXME unreliable
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(PlumbleActivity.this, R.string.trust_add_failed, Toast.LENGTH_LONG).show();
                        }
                    }
                });
                adb.setNegativeButton(R.string.wizard_cancel, null);
                adb.show();
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPermissionDenied(String reason) throws RemoteException {
            AlertDialog.Builder adb = new AlertDialog.Builder(PlumbleActivity.this);
            adb.setTitle(R.string.perm_denied);
            adb.setMessage(reason);
            adb.show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSettings = Settings.getInstance(this);
        setTheme(mSettings.getTheme()); // Set custom theme

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        mDatabase = new PlumbleSQLiteDatabase(this); // TODO add support for cloud storage

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerList.setOnItemClickListener(this);
        mDrawerAdapter = new DrawerAdapter(this, this);
        mDrawerList.setAdapter(mDrawerAdapter);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                supportInvalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // Tint logo to theme
        int iconColor = getTheme().obtainStyledAttributes(new int[] { android.R.attr.textColorPrimaryInverse }).getColor(0, -1);
        Drawable logo = getResources().getDrawable(R.drawable.ic_home);
        logo.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
        getSupportActionBar().setLogo(logo);

        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setIndeterminate(true);
        mConnectingDialog.setCancelable(true);
        mConnectingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                try {
                    mService.disconnect();
                    Toast.makeText(PlumbleActivity.this, R.string.cancelled, Toast.LENGTH_SHORT).show();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        AlertDialog.Builder dadb = new AlertDialog.Builder(this);
        dadb.setMessage(R.string.disconnectSure);
        dadb.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    if(mService != null && mService.isConnected()) mService.disconnect();
                    loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        dadb.setNegativeButton(android.R.string.cancel, null);
        mDisconnectPromptBuilder = dadb;

        // Restore old fragment or load server list (default)
        int fragmentId = DrawerAdapter.ITEM_FAVOURITES;
        if(savedInstanceState != null)
            fragmentId = savedInstanceState.getInt(SAVED_FRAGMENT_TAG, DrawerAdapter.ITEM_FAVOURITES);
        loadDrawerFragment(fragmentId);

        // If we're given a Mumble URL to show, open up a server edit fragment.
        if(getIntent() != null &&
                Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            String url = getIntent().getDataString();
            try {
                Server server = MumbleURLParser.parseURL(url);

                // Open a dialog prompting the user to add the Mumble server.
                Bundle args = new Bundle();
                args.putBoolean("save", false);
                args.putParcelable("server", server);
                ServerEditFragment fragment = (ServerEditFragment) ServerEditFragment.instantiate(this, ServerEditFragment.class.getName(), args);
                fragment.show(getSupportFragmentManager(), "url_edit");
            } catch (MalformedURLException e) {
                Toast.makeText(this, getString(R.string.mumble_url_parse_failed), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
        if(mSettings.isFirstRun()) showSetupWizard();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent connectIntent = new Intent(this, PlumbleService.class);
        bindService(connectIntent, mConnection, BIND_AUTO_CREATE);

        Intent resumeIntent = new Intent(ACTION_PLUMBLE_SHOWN);
        sendBroadcast(resumeIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mService != null)
            try {
                mService.unregisterObserver(mObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        unbindService(mConnection);
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem disconnectButton = menu.findItem(R.id.action_disconnect);
        try {
            disconnectButton.setVisible(mService != null && mService.isConnected());
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Color the action bar icons to the primary text color of the theme, TODO optimize
        int foregroundColor = getTheme().obtainStyledAttributes(new int[] { android.R.attr.textColorPrimaryInverse }).getColor(0, -1);
        for(int x=0;x<menu.size();x++) {
            MenuItem item = menu.getItem(x);
            if(item.getIcon() != null) {
                Drawable icon = item.getIcon().mutate(); // Mutate the icon so that the color filter is exclusive to the action bar
                icon.setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.plumble, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(mDrawerToggle.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.action_disconnect:
                try {
                    getService().disconnect();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return true;
            case R.id.action_settings:
                Intent prefIntent = new Intent(this, Preferences.class);
                startActivity(prefIntent);
                return true;
        }

        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Persist active fragment across rotations
        outState.putInt(SAVED_FRAGMENT_TAG, mActiveFragment);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        try {
            if(Settings.ARRAY_INPUT_METHOD_PTT.equals(mSettings.getInputMethod()) &&
                    keyCode == mSettings.getPushToTalkKey() &&
                    mService != null &&
                    mService.isConnected()) {
                if(!mService.isTalking() && !mSettings.isPushToTalkToggle()) {
                    mService.setTalkingState(true);
                }
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            if(Settings.ARRAY_INPUT_METHOD_PTT.equals(mSettings.getInputMethod()) &&
                    keyCode == mSettings.getPushToTalkKey() &&
                    mService != null &&
                    mService.isConnected()) {
                if(!mSettings.isPushToTalkToggle() && mService.isTalking()) {
                    mService.setTalkingState(false);
                } else {
                    mService.setTalkingState(!mService.isTalking());
                }
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        try {
            if(mService.isConnected()) {
                mDisconnectPromptBuilder.show();
                return;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onBackPressed();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mDrawerLayout.closeDrawers();
        loadDrawerFragment((int) id);
    }

    /**
     * Shows a nice looking setup wizard to guide the user through the app's settings.
     * Will do nothing if it isn't the first launch.
     */
    private void showSetupWizard() {
        // Prompt the user to generate a certificate, FIXME
        if(mSettings.isUsingCertificate()) return;
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(R.string.first_run_generate_certificate_title);
        adb.setMessage(R.string.first_run_generate_certificate);
        adb.setPositiveButton(R.string.generate, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PlumbleCertificateGenerateTask generateTask = new PlumbleCertificateGenerateTask(PlumbleActivity.this) {
                    @Override
                    protected void onPostExecute(File result) {
                        super.onPostExecute(result);
                        mSettings.setCertificatePath(result.getAbsolutePath());
                    }
                };
                generateTask.execute();
            }
        });
        adb.show();
        mSettings.setFirstRun(false);

        // TODO: finish wizard
//        Intent intent = new Intent(this, WizardActivity.class);
//        startActivity(intent);
    }

    /**
     * Loads a fragment from the drawer.
     */
    private void loadDrawerFragment(int fragmentId) {
        Class<? extends Fragment> fragmentClass = null;
        Bundle args = new Bundle();
        switch (fragmentId) {
            case DrawerAdapter.ITEM_SERVER:
                fragmentClass = ChannelFragment.class;
                break;
            case DrawerAdapter.ITEM_INFO:
                fragmentClass = ServerInfoFragment.class;
                break;
            case DrawerAdapter.ITEM_ACCESS_TOKENS:
                fragmentClass = AccessTokenFragment.class;
                try {
                    args.putLong("server", mService.getConnectedServer().getId());
                    args.putStringArrayList("access_tokens", (ArrayList<String>) mDatabase.getAccessTokens(mService.getConnectedServer().getId()));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            case DrawerAdapter.ITEM_PINNED_CHANNELS:
                fragmentClass = ChannelFragment.class;
                args.putBoolean("pinned", true);
                break;
            case DrawerAdapter.ITEM_FAVOURITES:
                fragmentClass = ServerListFragment.class;
                break;
            case DrawerAdapter.ITEM_LAN:
                break;
            case DrawerAdapter.ITEM_PUBLIC:
                fragmentClass = PublicServerListFragment.class;
                break;
            default:
                return;
        }
        Fragment fragment = Fragment.instantiate(this, fragmentClass.getName(), args);
        getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, fragment, fragmentClass.getName())
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        setTitle(mDrawerAdapter.getItemWithId(fragmentId).title);
        mActiveFragment = fragmentId;
    }

    public void connectToServer(final Server server) {
        // Check if we're already connected to a server; if so, inform user.
        try {
            if(mService != null && mService.isConnected()) {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setMessage(R.string.reconnect_dialog_message);
                adb.setPositiveButton(R.string.connect, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            // Register an observer to reconnect to the new server once disconnected.
                            mService.registerObserver(new JumbleObserver() {
                                @Override
                                public void onDisconnected() throws RemoteException {
                                    connectToServer(server);
                                    mService.unregisterObserver(this);
                                }
                            });
                            mService.disconnect();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
                adb.setNegativeButton(android.R.string.cancel, null);
                adb.show();
                return;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mConnectingDialog.setMessage(getString(R.string.connecting_to_server, server.getHost(), server.getPort()));
        mConnectingDialog.show();

        /* Convert input method defined in settings to an integer format used by Jumble. */
        int inputMethod = 0;
        String prefInputMethod = mSettings.getInputMethod();
        if(Settings.ARRAY_INPUT_METHOD_VOICE.equals(prefInputMethod))
            inputMethod = Constants.TRANSMIT_VOICE_ACTIVITY;
        else if(Settings.ARRAY_INPUT_METHOD_PTT.equals(prefInputMethod))
            inputMethod = Constants.TRANSMIT_PUSH_TO_TALK;
        else if(Settings.ARRAY_INPUT_METHOD_CONTINUOUS.equals(prefInputMethod) ||
                Settings.ARRAY_INPUT_METHOD_HANDSET.equals(prefInputMethod))
            inputMethod = Constants.TRANSMIT_CONTINUOUS;

        int audioSource = Settings.ARRAY_INPUT_METHOD_HANDSET.equals(mSettings.getInputMethod()) ?
                MediaRecorder.AudioSource.DEFAULT : MediaRecorder.AudioSource.MIC;
        int audioStream = Settings.ARRAY_INPUT_METHOD_HANDSET.equals(mSettings.getInputMethod()) ?
                AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;

        String applicationVersion = "";
        try {
            applicationVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Intent connectIntent = new Intent(this, PlumbleService.class);
        connectIntent.putExtra(JumbleService.EXTRAS_SERVER, server);
        connectIntent.putExtra(JumbleService.EXTRAS_CLIENT_NAME, getString(R.string.app_name)+" "+applicationVersion);
        connectIntent.putExtra(JumbleService.EXTRAS_TRANSMIT_MODE, inputMethod);
        connectIntent.putExtra(JumbleService.EXTRAS_DETECTION_THRESHOLD, mSettings.getDetectionThreshold());
        connectIntent.putExtra(JumbleService.EXTRAS_AMPLITUDE_BOOST, mSettings.getAmplitudeBoostMultiplier());
        connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE, mSettings.getCertificate());
        connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE_PASSWORD, mSettings.getCertificatePassword());
        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, mSettings.isAutoReconnectEnabled());
        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, RECONNECT_DELAY);
        connectIntent.putExtra(JumbleService.EXTRAS_USE_OPUS, !mSettings.isOpusDisabled());
        connectIntent.putExtra(JumbleService.EXTRAS_INPUT_RATE, mSettings.getInputSampleRate());
        connectIntent.putExtra(JumbleService.EXTRAS_INPUT_QUALITY, mSettings.getInputQuality());
        connectIntent.putExtra(JumbleService.EXTRAS_FORCE_TCP, mSettings.isTcpForced());
        connectIntent.putExtra(JumbleService.EXTRAS_USE_TOR, mSettings.isTorEnabled());
        connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, (ArrayList<String>) mDatabase.getAccessTokens(server.getId()));
        connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_SOURCE, audioSource);
        connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_STREAM, audioStream);
        connectIntent.putExtra(JumbleService.EXTRAS_FRAMES_PER_PACKET, mSettings.getFramesPerPacket());
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE, PlumbleTrustStore.getTrustStorePath(this));
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_PASSWORD, PlumbleTrustStore.getTrustStorePassword());
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_FORMAT, PlumbleTrustStore.getTrustStoreFormat());
        connectIntent.setAction(JumbleService.ACTION_CONNECT);
        startService(connectIntent);
    }

    public void connectToPublicServer(final PublicServer server) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);

        final Settings settings = Settings.getInstance(this);

        // Allow username entry
        final EditText usernameField = new EditText(this);
        usernameField.setHint(settings.getDefaultUsername());
        alertBuilder.setView(usernameField);

        alertBuilder.setTitle(R.string.connectToServer);

        alertBuilder.setPositiveButton(R.string.connect, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PublicServer newServer = server;
                if(!usernameField.getText().toString().equals(""))
                    newServer.setUsername(usernameField.getText().toString());
                else
                    newServer.setUsername(settings.getDefaultUsername());
                connectToServer(newServer);
            }
        });

        alertBuilder.show();
    }

    @Override
    public void serverInfoUpdated() {
        loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
    }

    /*
     * HERE BE IMPLEMENTATIONS
     */

    @Override
    public IJumbleService getService() {
        return mService;
    }

    @Override
    public PlumbleDatabase getDatabase() {
        return mDatabase;
    }

    @Override
    public void addServiceFragment(JumbleServiceFragment fragment) {
        mServiceFragments.add(fragment);
    }

    @Override
    public void removeServiceFragment(JumbleServiceFragment fragment) {
        mServiceFragments.remove(fragment);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(Settings.PREF_THEME.equals(key)) {
            // Recreate activity when theme is changed
            if(Build.VERSION.SDK_INT >= 11)
                recreate();
            else {
                Intent intent = new Intent(this, PlumbleActivity.class);
                finish();
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return mService != null && mService.isConnected();
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getConnectedServerName() {
        try {
            if(mService != null && mService.isConnected()) {
                Server server = mService.getConnectedServer();
                return server.getName().equals("") ? server.getHost() : server.getName();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }
}
