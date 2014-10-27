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

package com.morlunk.mumbleclient.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.morlunk.mumbleclient.R;

/**
 * A wrapper around a {@link android.view.MenuInflater} that tints menu items to the control color
 * of the action bar's theme.
 * Created by andrew on 27/10/14.
 */
public class TintedMenuInflater {
    private MenuInflater mInflater;
    private int mTintColour;

    public TintedMenuInflater(Context context) {
        this(context, new MenuInflater(context));
    }

    public TintedMenuInflater(Context context, MenuInflater inflater) {
        mInflater = inflater;
        TypedArray actionBarThemeArray =
                context.obtainStyledAttributes(new int[]{R.attr.actionBarStyle});
        int actionBarTheme = actionBarThemeArray.getResourceId(0, 0);
        actionBarThemeArray.recycle();

        TypedArray titleTextStyleArray =
                context.obtainStyledAttributes(actionBarTheme, new int[]{R.attr.titleTextStyle});
        int titleTextStyle = titleTextStyleArray.getResourceId(0, 0);
        titleTextStyleArray.recycle();

        TypedArray textColorArray =
                context.obtainStyledAttributes(titleTextStyle, new int[]{android.R.attr.textColor});
        mTintColour = textColorArray.getColor(0, 0);
        textColorArray.recycle();
    }

    public void inflate(int menuRes, Menu menu) {
        mInflater.inflate(menuRes, menu);
        for (int x = 0; x < menu.size(); x++) {
            MenuItem item = menu.getItem(x);
            tintItem(item);
        }
    }

    public void tintItem(MenuItem item) {
        if (item.getIcon() != null) {
            Drawable icon = item.getIcon().mutate();
            icon.setColorFilter(mTintColour, PorterDuff.Mode.MULTIPLY);
        }
    }
}
