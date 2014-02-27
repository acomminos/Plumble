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

/* The following code was written by Matthew Wiggins
 * and is released under the Apache 2.0 license
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * I added some extra functionality like a multiplier + better persistence. 
 * - Andrew Comminos
 */
package com.morlunk.mumbleclient.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;


public class SeekBarDialogPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private SeekBar mSeekBar;
    private TextView mSplashText, mValueText;
    private Context mContext;
    private String mDialogMessage, mSuffix;
    private int mDefault, mMax, mMin, mValue, mMultiplier = 0;

    public SeekBarDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mDialogMessage = attrs.getAttributeValue(ANDROID_NS, "dialogMessage");
        mSuffix = attrs.getAttributeValue(ANDROID_NS, "text");
        mDefault = attrs.getAttributeIntValue(ANDROID_NS, "defaultValue", 0);
        mMax = attrs.getAttributeIntValue(null, "max", 100);
        mMin = attrs.getAttributeIntValue(null, "min", 0);
        mMultiplier = attrs.getAttributeIntValue(null, "multiplier", 1);

    }

    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        mSplashText = new TextView(mContext);
        if (mDialogMessage != null)
            mSplashText.setText(mDialogMessage);
        layout.addView(mSplashText);

        mValueText = new TextView(mContext);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(18);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(mValueText, params);

        mSeekBar = new SeekBar(mContext);
        mSeekBar.setOnSeekBarChangeListener(this);
        layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (shouldPersist())
            mValue = getPersistedInt(-1) != -1 ? getPersistedInt(mDefault) / mMultiplier : getPersistedInt(mDefault);

        mSeekBar.setMax(mMax - mMin);
        mSeekBar.setProgress(mValue - mMin);
        return layout;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mSeekBar.setMax(mMax - mMin);
        mSeekBar.setProgress(mValue - mMin);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        super.onSetInitialValue(restore, defaultValue);
        if (restore)
            mValue = (shouldPersist() ? getPersistedInt(mDefault) / mMultiplier : 0);
        else
            mValue = ((Integer) defaultValue / mMultiplier);
    }

    public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
        String t = String.valueOf((value * mMultiplier) + (mMultiplier * mMin));
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    }

    public void onStartTrackingTouch(SeekBar seek) {
    }

    public void onStopTrackingTouch(SeekBar seek) {
    }

    /* (non-Javadoc)
       * @see android.preference.DialogPreference#onDialogClosed(boolean)
       */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && shouldPersist()) {
            persistInt(mMultiplier * (mMin + mSeekBar.getProgress()));
        }
    }
}