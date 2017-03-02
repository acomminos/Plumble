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

package com.morlunk.mumbleclient.channel;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.morlunk.mumbleclient.Constants;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.db.DatabaseProvider;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;

import java.util.ArrayList;
import java.util.List;

public class AccessTokenFragment extends JumbleServiceFragment {

    public interface AccessTokenListener {
        public void onAccessTokenAdded(long serverId, String token);
        public void onAccessTokenRemoved(long serverId, String token);
    }

	private List<String> mTokens;
	
	private ListView mTokenList;
	private TokenAdapter mTokenAdapter;
	private EditText mTokenField;

    private DatabaseProvider mProvider;

    @Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

        mTokens = new ArrayList<String>(getAccessTokens());
        mTokenAdapter = new TokenAdapter(activity, mTokens);

        try {
            mProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DatabaseProvider");
        }
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_tokens, container, false);

        mTokenList = (ListView) view.findViewById(R.id.tokenList);
        mTokenList.setAdapter(mTokenAdapter);

        mTokenField = (EditText) view.findViewById(R.id.tokenField);
        mTokenField.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if(actionId == EditorInfo.IME_ACTION_SEND) {
					addToken();
					return true;
				}
				return false;
			}
		});
		
		ImageButton addButton = (ImageButton) view.findViewById(R.id.tokenAddButton);
		addButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				addToken();
			}
		});
		
		return view;
	}

	private void addToken() {
		String tokenText = mTokenField.getText().toString().trim();
		
		if(tokenText.equals(""))
			return;

        mTokenField.setText("");
		
		Log.i(Constants.TAG, "Adding token: " + tokenText);
		
		mTokens.add(tokenText);
		mTokenAdapter.notifyDataSetChanged();

		mTokenList.smoothScrollToPosition(mTokens.size() - 1);
        mProvider.getDatabase().addAccessToken(getServerId(), tokenText);
		if (getService().isConnected()) {
			getService().getSession().sendAccessTokens(mTokens);
		}
    }

    private long getServerId() {
        return getArguments().getLong("server");
    }

    private List<String> getAccessTokens() {
        return getArguments().getStringArrayList("access_tokens");
    }
	
	private class TokenAdapter extends ArrayAdapter<String> {

		public TokenAdapter(Context context,
				List<String> objects) {
			super(context, android.R.layout.simple_list_item_1, objects);
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if(convertView == null) {
				view = getActivity().getLayoutInflater().inflate(R.layout.token_row, null, false);
			}
			
			final String token = getItem(position);
			
			TextView title = (TextView) view.findViewById(R.id.tokenItemTitle);
			title.setText(token);
			
			ImageButton deleteButton = (ImageButton) view.findViewById(R.id.tokenItemDelete);
			deleteButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
                    mTokens.remove(position);
					notifyDataSetChanged();
                    mProvider.getDatabase().removeAccessToken(getServerId(), token);
					if (getService().isConnected()) {
						getService().getSession().sendAccessTokens(mTokens);
					}
                }
			});

			return view;
		}
		
	}

}
