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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;

/**
 * Created by andrew on 30/03/14.
 */
public class CardDrawable {

    /**
     * Returns the appropriate drawable for the current them.
     * @param context The context to use to fetch the theme.
     * @return The best card drawable for the current theme.
     */
    public static Drawable getDrawable(Context context) {
        Settings settings = Settings.getInstance(context);
        int theme = settings.getTheme();
        switch (theme) {
            case R.style.Theme_Plumble:
                return context.getResources().getDrawable(R.drawable.server_card);
            default:
                int bg = context.getTheme().obtainStyledAttributes(theme, new int[] { R.attr.secondaryBackground }).getColor(0, 0);
                return new ColorDrawable(bg);

        }
    }

    private CardDrawable() {

    }
}
