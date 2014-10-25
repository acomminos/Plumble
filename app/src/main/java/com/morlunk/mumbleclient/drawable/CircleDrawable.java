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

package com.morlunk.mumbleclient.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.morlunk.mumbleclient.R;

/**
 * A drawable containing a circular bitmap in the style of @drawable/outline_circle_talking_off.
 * Created by andrew on 19/10/14.
 */
public class CircleDrawable extends Drawable {
    public static final int STROKE_WIDTH_DP = 1;
    private Resources mResources;
    private Bitmap mBitmap;
    private Paint mPaint;
    private Paint mStrokePaint;
    private ConstantState mConstantState;

    public CircleDrawable(Resources resources, Bitmap bitmap) {
        mResources = resources;
        mBitmap = bitmap;

        mPaint = new Paint();
        mPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        mPaint.setDither(true);
        mPaint.setAntiAlias(true);

        mStrokePaint = new Paint();
        mStrokePaint.setDither(true);
        mStrokePaint.setAntiAlias(true);
        mStrokePaint.setColor(resources.getColor(R.color.ripple_talk_state_disabled));
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                STROKE_WIDTH_DP, resources.getDisplayMetrics());
        mStrokePaint.setStrokeWidth(strokeWidth);
        mStrokePaint.setStyle(Paint.Style.STROKE);

        mConstantState = new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new CircleDrawable(mResources, mBitmap);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        RectF bitmapRect = new RectF(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        Matrix matrix = new Matrix();
        matrix.setRectToRect(bitmapRect, new RectF(bounds), Matrix.ScaleToFit.CENTER);
        mPaint.getShader().setLocalMatrix(matrix);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF imageRect = new RectF(getBounds());
        RectF strokeRect = new RectF(getBounds());
        // Default stroke drawing is both inset and outset.
        strokeRect.inset(mStrokePaint.getStrokeWidth()/2,
                         mStrokePaint.getStrokeWidth()/2);

        canvas.drawOval(imageRect, mPaint);
        canvas.drawOval(strokeRect, mStrokePaint);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public ConstantState getConstantState() {
        return mConstantState;
    }
}
