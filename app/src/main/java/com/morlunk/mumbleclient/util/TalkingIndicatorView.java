/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import com.morlunk.mumbleclient.R;

import java.util.Objects;

/**
 * Created by andrew on 30/11/15.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class TalkingIndicatorView extends View implements ValueAnimator.AnimatorUpdateListener {
    private static final float OVERSHOOT_TENSION = 3.5f;

    /** The offset of the arc from the origin, mod 360. */
    private float mArcOffset;
    /** The length of the arc, in radians. */
    private float mArcLength;
    private float mArcWidth;
    private Paint mArcPaint;
    private RectF mBounds;

    public TalkingIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray style = context.obtainStyledAttributes(attrs, new int[] {
                R.attr.arcColor,
                R.attr.arcWidth,
                R.attr.arcLength,
                R.attr.cyclePeriod
        });

        mArcOffset = 0;
        mArcLength = style.getDimensionPixelSize(2, 20);
        mArcWidth = style.getDimensionPixelSize(1, 4);

        mArcPaint = new Paint();
        mArcPaint.setAntiAlias(true);
        mArcPaint.setColor(style.getColor(0, 0xFFFF0000));
        mArcPaint.setStrokeWidth(mArcWidth);
        mArcPaint.setStyle(Paint.Style.STROKE);

        ValueAnimator cycleAnimator = new ValueAnimator();
        cycleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        cycleAnimator.setDuration(style.getInteger(3, 1000));
        cycleAnimator.setFloatValues(0, 360);
        cycleAnimator.setInterpolator(new LinearInterpolator());
        cycleAnimator.addUpdateListener(this);
        cycleAnimator.start();

        style.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawArc(mBounds, mArcOffset, mArcLength, false, mArcPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBounds = new RectF(0, 0, w, h);
        mBounds.inset(mArcWidth / 2, mArcWidth / 2);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mArcOffset = (float) animation.getAnimatedValue();
        invalidate();
    }

    public float getStrokeWidth() {
        return mArcWidth;
    }
}
