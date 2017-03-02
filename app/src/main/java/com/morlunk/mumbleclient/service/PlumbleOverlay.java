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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;

import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.channel.ChannelAdapter;

/**
 * An onscreen interactive overlay displaying the users in the current channel.
 * Created by andrew on 26/09/13.
 */
public class PlumbleOverlay {

    public static final int DEFAULT_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 240;

    private JumbleObserver mObserver = new JumbleObserver() {
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            mChannelAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if(user.getChannel() != null &&
                    user.getChannel().equals(mService.getSessionChannel()))
                mChannelAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if(user.getSession() == mService.getSessionId()) // Session user has changed channels
                mChannelAdapter.setChannel(mService.getSessionChannel());
            else if(newChannel.getId() == mService.getSessionChannel().getId() ||
                    oldChannel.getId() == mService.getSessionChannel().getId())
                mChannelAdapter.notifyDataSetChanged();
        }
    };

    private View mOverlayView;
    private ListView mOverlayList;
    private ChannelAdapter mChannelAdapter;
    private ImageView mTalkButton;
//    private ImageView mToggleButton;
    private ImageView mCloseButton;
    private ImageView mDragButton;
    private View mTitleView;
    private WindowManager.LayoutParams mOverlayParams;
    private boolean mShown = false;
//    private boolean mShowChat = false;

    private PlumbleService mService;

    public PlumbleOverlay(PlumbleService service) {
        mService = service;
        mOverlayView = View.inflate(service, R.layout.overlay, null);
        mTalkButton = (ImageView) mOverlayView.findViewById(R.id.overlay_talk);
        mDragButton = (ImageView) mOverlayView.findViewById(R.id.overlay_drag);
        mCloseButton = (ImageView) mOverlayView.findViewById(R.id.overlay_close);
//        mToggleButton = (ImageView) mOverlayView.findViewById(R.id.overlay_mode_toggle);
        mTitleView = mOverlayView.findViewById(R.id.overlay_title);
        mOverlayList = (ListView) mOverlayView.findViewById(R.id.overlay_list);

        mTitleView.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager mWindowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float mInitialX;
            private float mInitialY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(MotionEvent.ACTION_DOWN == event.getAction()) {
                    mInitialX = event.getRawX() - mOverlayParams.x;
                    mInitialY = event.getRawY() - mOverlayParams.y;
                    return true;
                } else if(MotionEvent.ACTION_MOVE == event.getAction()) {
                    mOverlayParams.x = (int) (event.getRawX() - mInitialX);
                    mOverlayParams.y = (int) (event.getRawY() - mInitialY);
                    mWindowManager.updateViewLayout(mOverlayView, mOverlayParams);
                    return true;
                }
                return false;
            }
        });

        mDragButton.setOnTouchListener(new View.OnTouchListener() {

            private final WindowManager mWindowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float mInitialX;
            private float mInitialY;
            private float mInitialWidth;
            private float mInitialHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mInitialX = event.getRawX();
                        mInitialY = event.getRawY();
                        mInitialWidth = mOverlayView.getWidth();
                        mInitialHeight = mOverlayView.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mOverlayParams.width = (int) (mInitialWidth + (event.getRawX() - mInitialX));
                        mOverlayParams.height = (int) (mInitialHeight + (event.getRawY() - mInitialY));
                        mWindowManager.updateViewLayout(mOverlayView, mOverlayParams);
                        return true;
                }
                return false;
            }
        });

        /*
        mToggleButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mShowChat = !mShowChat;
                // TODO implement chat
                if(mShowChat) {

                } else {

                }
            }
        });
        */

        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(MotionEvent.ACTION_DOWN == event.getAction()) {
                    mService.setTalkingState(true);
                    return true;
                } else if(MotionEvent.ACTION_UP == event.getAction()) {
                    mService.setTalkingState(false);
                    return true;
                }
                return false;
            }
        });

        Settings settings = Settings.getInstance(service);
        boolean usingPtt = Settings.ARRAY_INPUT_METHOD_PTT.equals(settings.getInputMethod());
        setPushToTalkShown(usingPtt);

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        DisplayMetrics metrics = mService.getResources().getDisplayMetrics();
        mOverlayParams = new WindowManager.LayoutParams((int)(DEFAULT_WIDTH*metrics.density),
                (int)(DEFAULT_HEIGHT*metrics.density),
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        mOverlayParams.windowAnimations = android.R.style.Animation_Dialog;
    }

    public boolean isShown() {
        return mShown;
    }

    public void show() {
        if(mShown)
            return;
        mShown = true;
        mChannelAdapter = new ChannelAdapter(mService, mService.getSessionChannel());
        mOverlayList.setAdapter(mChannelAdapter);
        mService.registerObserver(mObserver);
        WindowManager windowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(mOverlayView, mOverlayParams);
    }

    public void hide() {
        if(!mShown)
            return;
        mShown = false;
        mService.unregisterObserver(mObserver);
        mOverlayList.setAdapter(null);
        try {
            WindowManager windowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(mOverlayView);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void setPushToTalkShown(boolean showPtt) {
        mTalkButton.setVisibility(showPtt ? View.VISIBLE : View.GONE);
    }
}
