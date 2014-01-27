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

package com.morlunk.mumbleclient.view;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.morlunk.mumbleclient.view.PlumbleNestedAdapter.NestMetadataType;
import com.morlunk.mumbleclient.view.PlumbleNestedAdapter.NestPositionMetadata;

public class PlumbleNestedListView extends ListView implements OnItemClickListener {

	public interface OnNestedChildClickListener {
		public void onNestedChildClick(AdapterView<?> parent, View view, int groupId, int childPosition);
	}
	public interface OnNestedGroupClickListener {
		public void onNestedGroupClick(AdapterView<?> parent, View view, int groupId);
	}
	
	private PlumbleNestedAdapter mNestedAdapter;
	private OnNestedChildClickListener mChildClickListener;
	private OnNestedGroupClickListener mGroupClickListener;

	//private boolean mMaintainPosition;
	
	public PlumbleNestedListView(Context context) {
		this(context, null);
	}
	
	public PlumbleNestedListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setOnItemClickListener(this);
		/*
		TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PlumbleNestedListView, 0, 0);
		try {
			mMaintainPosition = array.getBoolean(R.styleable.PlumbleNestedListView_maintainPosition, false);
		} finally {
			array.recycle();
		}
		*/
	}
	
	public void setAdapter(PlumbleNestedAdapter adapter) {
		super.setAdapter(adapter);
		mNestedAdapter = adapter;
	}
	
	public void expandGroup(int groupId) {
		mNestedAdapter.expandGroup(groupId);
	}
	
	public void collapseGroup(int groupId) {
		mNestedAdapter.collapseGroup(groupId);
	}

	public OnNestedChildClickListener getOnChildClickListener() {
		return mChildClickListener;
	}

	public void setOnChildClickListener(
			OnNestedChildClickListener mChildClickListener) {
		this.mChildClickListener = mChildClickListener;
	}
	
	public OnNestedGroupClickListener getOnGroupClickListener() {
		return mGroupClickListener;
	}

	public void setOnGroupClickListener(
			OnNestedGroupClickListener mGroupClickListener) {
		this.mGroupClickListener = mGroupClickListener;
	}

	@Override
	public void setOnItemClickListener(OnItemClickListener listener) {
		if(listener != this)
	        throw new RuntimeException(
	                "For PlumbleNestedListView, please use the child and group equivalents of setOnItemClickListener.");
		super.setOnItemClickListener(listener);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		NestPositionMetadata metadata = (NestPositionMetadata) mNestedAdapter.mVisibleMeta.get(position);
		if(metadata.type == NestMetadataType.META_TYPE_GROUP && mGroupClickListener != null) {
			mGroupClickListener.onNestedGroupClick(parent, view, metadata.id);
		} else if(metadata.type == NestMetadataType.META_TYPE_ITEM && mChildClickListener != null)
			mChildClickListener.onNestedChildClick(parent, view, metadata.parent.id, metadata.childPosition);
	}
}
