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
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;

public class ServerEditFragment extends DialogFragment {
    private static final String ARGUMENT_SERVER = "server";
    private static final String ARGUMENT_ACTION = "action";
    private static final String ARGUMENT_IGNORE_TITLE = "ignore_title";

	private EditText mNameEdit;
	private EditText mHostEdit;
	private EditText mPortEdit;
	private EditText mUsernameEdit;
    private EditText mPasswordEdit;
	
	private ServerEditListener mListener;

    /**
     * Creates a new {@link ServerEditFragment} dialog. Results will be delivered to the parent
     * activity via {@link ServerEditListener}.
     * @param server Optional, if set will populate the fragment with data from the server.
     * @param action The action the fragment is performing (i.e. Add, Edit)
     * @param ignoreTitle If true, don't show fields related to the server title (useful for quick
     *                    connect dialogs)
     */
    public static DialogFragment createServerEditDialog(Context context, Server server,
                                                  Action action,
                                                  boolean ignoreTitle) {
        Bundle args = new Bundle();
        args.putParcelable(ARGUMENT_SERVER, server);
        args.putInt(ARGUMENT_ACTION, action.ordinal());
        args.putBoolean(ARGUMENT_IGNORE_TITLE, ignoreTitle);
        return (DialogFragment) Fragment.instantiate(context, ServerEditFragment.class.getName(), args);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
        try {
            mListener = (ServerEditListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ServerEditListener!");
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
                    Server server = createServer();
                    mListener.onServerEdited(getAction(), server);
                    dismiss();
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        Settings settings = Settings.getInstance(getActivity());

        String actionName;
        switch (getAction()) {
            case ADD_ACTION:
                actionName = getString(R.string.add);
                break;
            case EDIT_ACTION:
                actionName = getString(R.string.edit);
                break;
            case CONNECT_ACTION:
                actionName = getString(R.string.connect);
                break;
            default:
                throw new RuntimeException("Unknown action " + getAction());
        }
        adb.setPositiveButton(actionName, null);
        adb.setNegativeButton(android.R.string.cancel, null);

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.dialog_server_edit, null, false);

        TextView titleLabel = (TextView) view.findViewById(R.id.server_edit_name_title);
        mNameEdit = (EditText) view.findViewById(R.id.server_edit_name);
        mHostEdit = (EditText) view.findViewById(R.id.server_edit_host);
        mPortEdit = (EditText) view.findViewById(R.id.server_edit_port);
        mUsernameEdit = (EditText) view.findViewById(R.id.server_edit_username);
        mUsernameEdit.setHint(settings.getDefaultUsername());
        mPasswordEdit = (EditText) view.findViewById(R.id.server_edit_password);

        Server oldServer = getServer();
        if (oldServer != null) {
            mNameEdit.setText(oldServer.getName());
            mHostEdit.setText(oldServer.getHost());
            mPortEdit.setText(String.valueOf(oldServer.getPort()));
            mUsernameEdit.setText(oldServer.getUsername());
            mPasswordEdit.setText(oldServer.getPassword());
        }

        if (shouldIgnoreTitle()) {
            titleLabel.setVisibility(View.GONE);
            mNameEdit.setVisibility(View.GONE);
        }

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        adb.setInverseBackgroundForced(true);

        adb.setView(view);

        return adb.create();
    }

	public Server createServer() {
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

        // Inherit database ID of provided server.
        long id;
        if (getServer() != null) {
            id = getServer().getId();
        } else {
            id = -1;
        }

        return new Server(id, name, host, port, username, password);
	}

    /**
     * Checks all fields in this ServerEditFragment for validity.
     * If an invalid field is found, an error is shown and false is returned.
     * @return true if the inputted values are valid, false otherwise.
     */
    public boolean validate() {
        if (mHostEdit.getText().length() == 0) {
            mHostEdit.setError(getString(R.string.invalid_host));
            return false;
        } else if (mPortEdit.getText().length() > 0) {
            try {
                int port = Integer.parseInt(mPortEdit.getText().toString());
                if (port < 0 || port > 65535) {
                    mPortEdit.setError(getString(R.string.invalid_port_range));
                    return false;
                }
            } catch (NumberFormatException nfe) {
                mPortEdit.setError(getString(R.string.invalid_port_range));
                return false;
            }
        }
        return true;
    }

    private Server getServer() {
        return getArguments().getParcelable(ARGUMENT_SERVER);
    }

    private Action getAction() {
        return Action.values()[getArguments().getInt(ARGUMENT_ACTION)];
    }

    private boolean shouldIgnoreTitle() {
        return getArguments().getBoolean(ARGUMENT_IGNORE_TITLE);
    }

    public interface ServerEditListener {
        void onServerEdited(Action action, Server server);
    }

    public enum Action {
        CONNECT_ACTION,
        EDIT_ACTION,
        ADD_ACTION
    }
}
