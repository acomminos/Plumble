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

package com.morlunk.mumbleclient.channel.comment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TabHost;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.util.JumbleServiceProvider;

/**
 * Fragment to change your comment using basic WYSIWYG tools.
 * Created by andrew on 10/08/13.
 */
public abstract class AbstractCommentFragment extends DialogFragment {

    private TabHost mTabHost;
    private WebView mCommentView;
    private EditText mCommentEdit;
    private JumbleServiceProvider mProvider;
    private String mComment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mComment = getArguments().getString("comment");
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mProvider = (JumbleServiceProvider) activity;
        } catch (ClassCastException e) {
            throw new RuntimeException(activity.getClass().getName() + " must implement JumbleServiceProvider!");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.dialog_comment, null, false);

        mCommentView = (WebView) view.findViewById(R.id.comment_view);
        mCommentEdit = (EditText) view.findViewById(R.id.comment_edit);

        mTabHost = (TabHost) view.findViewById(R.id.comment_tabhost);
        mTabHost.setup();

        if(mComment == null) {
            mCommentView.loadData("Loading...", null, null);
            requestComment(mProvider.getService());
        } else {
            loadComment(mComment);
        }

        TabHost.TabSpec viewTab = mTabHost.newTabSpec("View");
        viewTab.setIndicator(getString(R.string.comment_view));
        viewTab.setContent(R.id.comment_tab_view);

        TabHost.TabSpec editTab = mTabHost.newTabSpec("Edit");
        editTab.setIndicator(getString(isEditing() ? R.string.comment_edit_source : R.string.comment_view_source));
        editTab.setContent(R.id.comment_tab_edit);

        mTabHost.addTab(viewTab);
        mTabHost.addTab(editTab);

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if("View".equals(tabId)) {
                    // When switching back to view tab, update with user's HTML changes.
                    mCommentView.loadData(mCommentEdit.getText().toString(), "text/html", "UTF-8");
                } else if("Edit".equals(tabId) && "".equals(mCommentEdit.getText().toString())) {
                    // Load edittext content for the first time when the tab is selected, to improve performance with long messages.
                    mCommentEdit.setText(mComment);
                }
            }
        });

        mTabHost.setCurrentTab(isEditing() ? 1 : 0);

        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        adb.setView(view);
        if (isEditing()) {
            adb.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    editComment(mProvider.getService(), mCommentEdit.getText().toString());
                }
            });
        }
        adb.setNegativeButton(R.string.close, null);
        return adb.create();
    }

    protected void loadComment(String comment) {
        if(mCommentView == null) return;
        mCommentView.loadData(comment, "text/html", "UTF-8");
        mComment = comment;
    }

    public boolean isEditing() {
        return getArguments().getBoolean("editing");
    }

    /**
     * Requests a comment from the service. Will not be called if we already have a comment provided.
     * This method is expected to set a callback that will call {@link com.morlunk.mumbleclient.channel.comment.AbstractCommentFragment#loadComment(String comment)}.
     * @param service The bound Jumble service to use for remote calls.
     */
    public abstract void requestComment(IJumbleService service);

    /**
     * Asks the service to replace the comment.
     * @param service The bound Jumble service to use for remote calls.
     * @param comment The comment the user has defined.
     */
    public abstract void editComment(IJumbleService service, String comment);
}
