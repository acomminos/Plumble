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
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import com.morlunk.mumbleclient.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for PlumbleNestedListView that is designed to hold a channel hierarchy.
 * The reason group ID is so essential instead of having an implementation-side position is to better abstract the hierarchy, which is messy and confusing.
 * @param <G> The group class.
 * @param <C> The child class.
 */
public abstract class PlumbleNestedAdapter<G, C> extends BaseAdapter implements ListAdapter {
	
	protected enum NestMetadataType {
		META_TYPE_GROUP,
		META_TYPE_ITEM
	}
	
	protected class NestPositionMetadata {
		NestMetadataType type;
        NestPositionMetadata parent;
        int id;
		int childPosition;
		int depth;
	}
	
	private Context mContext;

	/**
	 * Metadata representations
	 */
	protected List<NestPositionMetadata> mFlatMeta = new ArrayList<NestPositionMetadata>();
	protected List<NestPositionMetadata> mVisibleMeta = new ArrayList<NestPositionMetadata>();
    protected SparseBooleanArray mExpandedGroups = new SparseBooleanArray();
	
	public abstract View getGroupView(int groupId, int depth, View convertView, ViewGroup parent);
	public abstract View getChildView(int groupId, int childPosition, int depth, View convertView, ViewGroup parent);

    public abstract List<Integer> getRootIds();

	public abstract int getGroupCount(int parentId);
    public abstract int getGroupId(int parentId, int groupPosition);
	public abstract int getChildCount(int groupId);
	public abstract int getChildId(int groupId, int childPosition);

	public C getChild(int groupId, int childPosition) { return null; };
	public G getGroup(int groupId) { return null; };
	public boolean isGroupExpandedByDefault(int groupId) { return false; };
	
	public PlumbleNestedAdapter(Context context) {
		mContext = context;
	}

    private final void buildFlatMetadata() {
        List<NestPositionMetadata> metadata = new ArrayList<NestPositionMetadata>();
        // Start with root element(s), them populate downwards.
        for(int rootId : getRootIds()) {
            mExpandedGroups.put(rootId, true); // Always expand root
            NestPositionMetadata root = new NestPositionMetadata();
            root.id = rootId;
            root.type = NestMetadataType.META_TYPE_GROUP;
            populateHierarchy(root, metadata);
        }
        mFlatMeta = metadata;
    }

    /**
     * Recursively flatten the channel hierarchy, including both subchannels and users.
     * @param parent
     * @param metaList
     */
    private void populateHierarchy(NestPositionMetadata parent, final List<NestPositionMetadata> metaList) {
        metaList.add(parent);

        int childCount = getChildCount(parent.id);
        for(int x=0;x<childCount;x++) {
            NestPositionMetadata childMeta = new NestPositionMetadata();
            childMeta.parent = parent;
            childMeta.id = getChildId(parent.id, x);
            childMeta.type = NestMetadataType.META_TYPE_ITEM;
            childMeta.childPosition = x;
            childMeta.depth = parent.depth+1;
            metaList.add(childMeta);
        }

        int subgroups = getGroupCount(parent.id);
        for(int x=0;x<subgroups;x++) {
            NestPositionMetadata subMeta = new NestPositionMetadata();
            subMeta.parent = parent;
            subMeta.id = getGroupId(parent.id, x);
            subMeta.type = NestMetadataType.META_TYPE_GROUP;
            subMeta.depth = 1+parent.depth;
            populateHierarchy(subMeta, metaList);
        }
    }

	/**
	 * TODO move this over to PlumbleNestedListView
	 */
	protected final void buildVisibleMetadata() {
		long startTime = System.currentTimeMillis();
		mVisibleMeta.clear();
		for(int x=0;x< mFlatMeta.size();x++) {
            NestPositionMetadata metadata = mFlatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_GROUP) {
				if(isParentExpanded(metadata))
						mVisibleMeta.add(metadata);
			} else if(metadata.type == NestMetadataType.META_TYPE_ITEM) {
				if(mVisibleMeta.contains(metadata.parent) &&
                        mExpandedGroups.get(metadata.parent.id, isGroupExpandedByDefault(metadata.parent.id))) // Don't insert a child group with no parent.
					mVisibleMeta.add(metadata);
			}
		}
		Log.d(Constants.TAG, "OPT: built visible metadata, took "+(System.currentTimeMillis()-startTime)+"ms");
	}
	
	/**
	 * Iterates up the group hierarchy and returns whether or not any of the group's parents are not expanded.
	 */
	private boolean isParentExpanded(NestPositionMetadata metadata) {
		if(metadata.parent == null)
			return true; // Return true for top of tree.
		if(!mExpandedGroups.get(metadata.parent.id, isGroupExpandedByDefault(metadata.parent.id)))
			return false;
		else
			return isParentExpanded(metadata.parent);
	}
	
	protected void collapseGroup(int groupId) {
        mExpandedGroups.put(groupId, false);
	}
	
	protected void expandGroup(int groupId) {
        mExpandedGroups.put(groupId, true);
	}
	
	public boolean isGroupExpanded(int groupId) {
		return mExpandedGroups.get(groupId, isGroupExpandedByDefault(groupId));
	}

	public int getFlatChildPosition(int childId) {
		for(int x=0;x< mFlatMeta.size();x++) {
			NestPositionMetadata metadata = mFlatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.id == childId)
				return x;
		}
		return -1;
	}
	
	public int getVisibleFlatChildPosition(int childId) {
		for(int x=0;x< mVisibleMeta.size();x++) {
			NestPositionMetadata metadata = mVisibleMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.id == childId)
				return x;
		}
		return -1;
	}

	public int getFlatGroupPosition(int groupId) {
		for(int x=0;x< mFlatMeta.size();x++) {
			NestPositionMetadata metadata = mFlatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_GROUP &&
					metadata.id == groupId)
				return x;
		}
		return -1;
	}
	
	public int getVisibleFlatGroupPosition(int groupId) {
		for(int x=0;x< mVisibleMeta.size();x++) {
			NestPositionMetadata metadata = mVisibleMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.id == groupId)
				return x;
		}
		return -1;
	}
	
	@Override
	public void notifyDataSetChanged() {
		long startTime = System.currentTimeMillis();
		buildFlatMetadata();
		buildVisibleMetadata();
		super.notifyDataSetChanged();
		Log.d(Constants.TAG, "OPT: reloaded data set, took "+(System.currentTimeMillis()-startTime)+"ms");
	}
	
	/**
	 * Does not rebuild flat hierarchy metadata.
	 */
	protected void notifyVisibleSetChanged() {
		buildVisibleMetadata();
		super.notifyDataSetChanged();
	}
	
	@Override
	public final int getCount() {
		return mVisibleMeta.size();
	}
	
	@Override
	public int getViewTypeCount() {
		return 2;
	}
	
	@Override
	public int getItemViewType(int position) {
		NestPositionMetadata metadata = mVisibleMeta.get(position);
		return metadata.type.ordinal();
	}
	
	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public final Object getItem(int position) {
		NestPositionMetadata metadata = mVisibleMeta.get(position);
		if(metadata.type == NestMetadataType.META_TYPE_GROUP)
			return getGroup(metadata.id);
		else if(metadata.type == NestMetadataType.META_TYPE_ITEM)
			return getChild(metadata.id, metadata.childPosition);
		return null;
	}

	@Override
	public final View getView(int position, View convertView, ViewGroup parent) {
		NestPositionMetadata metadata = mVisibleMeta.get(position);
		NestMetadataType mType = NestMetadataType.values()[getItemViewType(position)];
		View view = null;
		if(mType == NestMetadataType.META_TYPE_GROUP)
			view = getGroupView(metadata.id, metadata.depth, convertView, parent);
		else if(mType == NestMetadataType.META_TYPE_ITEM)
			view = getChildView(metadata.parent.id, metadata.childPosition, metadata.depth, convertView, parent);
		return view;
	}
	
	public Context getContext() {
		return mContext;
	}

}
