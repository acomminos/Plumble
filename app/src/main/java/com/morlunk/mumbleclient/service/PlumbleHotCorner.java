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

package com.morlunk.mumbleclient.service;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.morlunk.mumbleclient.R;

/**
 * A hot corner in an area of the screen specified by {@link PlumbleHotCorner#getGravity()}.
 * Created by andrew on 07/06/14.
 */
public class PlumbleHotCorner implements View.OnTouchListener {
    private WindowManager mWindowManager;
    private Context mContext;
    private View mView;
    private boolean mShown;
    private int mHighlightColour;
    private PlumbleHotCornerListener mListener;
    private WindowManager.LayoutParams mParams;

    public PlumbleHotCorner(Context context, int gravity, PlumbleHotCornerListener listener) {
        if(listener == null) {
            throw new NullPointerException("A PlumbleHotCornerListener must be assigned.");
        }
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mContext = context;
        mView = inflater.inflate(R.layout.ptt_corner, null, false);
        mView.setOnTouchListener(this);
        mListener = listener;
        mParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = gravity;
        mHighlightColour = mContext.getResources().getColor(R.color.holo_blue_bright);
    }

    /**
     * Updates the hot corner with any new settings applied, recalculating the layout parameters.
     * Does nothing if the hot corner is not shown.
     */
    private void updateLayout() {
        if(!isShown()) return;
        mWindowManager.updateViewLayout(mView, mParams);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mView.setBackgroundColor(mHighlightColour);
                mListener.onHotCornerDown();
                return true;
            case MotionEvent.ACTION_UP:
                mView.setBackgroundColor(0);
                mListener.onHotCornerUp();
                return true;
            default:
                return false;
        }
    }

    public void setShown(boolean shown) {
        if(shown == mShown) return;
        if(shown) {
            mWindowManager.addView(mView, mParams);
        } else {
            mWindowManager.removeView(mView);
        }
        mShown = shown;
    }

    public boolean isShown() {
        return mShown;
    }

    public void setGravity(int gravity) {
        mParams.gravity = gravity;
        updateLayout();
    }

    public int getGravity() {
        return mParams.gravity;
    }

    public static interface PlumbleHotCornerListener {
        public void onHotCornerDown();
        public void onHotCornerUp();
    }
}
