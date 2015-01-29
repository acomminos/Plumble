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
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.db.DatabaseProvider;

public class ServerEditFragment extends DialogFragment {
    private TextView mNameTitle;
	private EditText mNameEdit;
	private EditText mHostEdit;
	private EditText mPortEdit;
	private EditText mUsernameEdit;
    private EditText mPasswordEdit;
    private TextView mErrorText;
	
	private ServerEditListener mListener;
    private DatabaseProvider mDatabaseProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

        try {
            mDatabaseProvider = (DatabaseProvider) activity;
            mListener = (ServerEditListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()+" must implement DatabaseProvider and ServerEditListener!");
        }
	}

    @Override
    public void onStart() {
        super.onStart();
        // Override positive button to not automatically dismiss on press.
        // We can't accomplish this with AlertDialog.Builder.
        ((AlertDialog)getDialog()).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validate()) {
                    Server server = createServer(shouldSave());

                    // If we're not committing this server, connect immediately.
                    if (!shouldSave()) mListener.connectToServer(server);

                    dismiss();
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        Settings settings = Settings.getInstance(getActivity());

        int actionTitle;
        if (shouldSave() && getServer() == null) {
            actionTitle = R.string.add;
        } else if (shouldSave()) {
            actionTitle = R.string.save;
        } else {
            actionTitle = R.string.connect;
        }

        adb.setPositiveButton(actionTitle, null);
        adb.setNegativeButton(android.R.string.cancel, null);

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.dialog_server_edit, null, false);

        mNameTitle = (TextView) view.findViewById(R.id.server_edit_name_title);
        mNameEdit = (EditText) view.findViewById(R.id.server_edit_name);
        mHostEdit = (EditText) view.findViewById(R.id.server_edit_host);
        mPortEdit = (EditText) view.findViewById(R.id.server_edit_port);
        mUsernameEdit = (EditText) view.findViewById(R.id.server_edit_username);
        mUsernameEdit.setHint(settings.getDefaultUsername());
        mPasswordEdit = (EditText) view.findViewById(R.id.server_edit_password);
        mErrorText = (TextView) view.findViewById(R.id.server_edit_error);
        if (getServer() != null) {
            Server oldServer = getServer();
            mNameEdit.setText(oldServer.getName());
            mHostEdit.setText(oldServer.getHost());
            mPortEdit.setText(String.valueOf(oldServer.getPort()));
            mUsernameEdit.setText(oldServer.getUsername());
            mPasswordEdit.setText(oldServer.getPassword());
        }

        if (!shouldSave()) {
            mNameTitle.setVisibility(View.GONE);
            mNameEdit.setVisibility(View.GONE);
        }

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        adb.setInverseBackgroundForced(true);

        adb.setView(view);

        return adb.create();
    }

    private boolean shouldSave() {
        return getArguments() == null || getArguments().getBoolean("save", true);
    }

    private Server getServer() {
        return getArguments() != null ? (Server) getArguments().getParcelable("server") : null;
    }

    /**
     * Creates or updates a server with the information in this fragment.
     * @param shouldCommit Whether to commit the created service to the DB.
     * @return The new or updated server.
     */
	public Server createServer(boolean shouldCommit) {
		String name = (mNameEdit).getText().toString().trim();
		String host = (mHostEdit).getText().toString().trim();

		int port;
		try {
			port = Integer.parseInt((mPortEdit).getText().toString());
		} catch (final NumberFormatException ex) {
			port = Constants.DEFAULT_PORT;
		}

		String username = (mUsernameEdit).getText().toString().trim();
        String password = mPasswordEdit.getText().toString();

        if (username.equals(""))
            username = mUsernameEdit.getHint().toString();

        Server server;

		if (getServer() != null) {
            server = getServer();
            server.setName(name);
            server.setHost(host);
            server.setPort(port);
            server.setUsername(username);
            server.setPassword(password);
			if(shouldCommit) mDatabaseProvider.getDatabase().updateServer(server);
		} else {
            server = new Server(-1, name, host, port, username, password);
			if(shouldCommit) mDatabaseProvider.getDatabase().addServer(server);
		}

        if(shouldCommit) mListener.serverInfoUpdated();

        return server;
	}

    /**
     * Checks all fields in this ServerEditFragment for validity.
     * If an invalid field is found, an error is shown and false is returned.
     * @return true if the inputted values are valid, false otherwise.
     */
    public boolean validate() {
        String error = null;

        if (mHostEdit.getText().length() == 0) {
            error = getString(R.string.invalid_host);
        } else if (mPortEdit.getText().length() > 0) {
            try {
                int port = Integer.parseInt(mPortEdit.getText().toString());
                if (port < 0 || port > 65535) {
                    error = getString(R.string.invalid_port_range);
                }
            } catch (NumberFormatException nfe) {
                error = getString(R.string.invalid_port_range);
            }
        }

        mErrorText.setVisibility(error != null ? View.VISIBLE : View.GONE);
        if (error != null) {
            mErrorText.setText(error);
            return false;
        } else {
            return true;
        }
    }

    public interface ServerEditListener {
        public void serverInfoUpdated();
        public void connectToServer(Server server);
    }
}
