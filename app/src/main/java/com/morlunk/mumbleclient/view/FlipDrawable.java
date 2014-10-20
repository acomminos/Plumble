package com.morlunk.mumbleclient.view;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/**
 * A transition drawable that shows a "flip" between two different images.
 * Created by andrew on 19/10/14.
 */
@TargetApi(11)
public class FlipDrawable extends LayerDrawable implements ValueAnimator.AnimatorUpdateListener {
    private Drawable mFrom;
    private Drawable mTo;
    private Camera mCamera;
    private float mRotate;

    public FlipDrawable(Drawable from, Drawable to) {
        super(new Drawable[] { from, to });
        mFrom = from;
        mTo = to;
        mCamera = new Camera();
        mRotate = 0;
    }

    @Override
    public void draw(Canvas canvas) {
        float centerX = getIntrinsicWidth()/2;
        float centerY = getIntrinsicHeight()/2;
        boolean flipped = mRotate > 90;
        mCamera.save();
        mCamera.translate(centerX, centerY, 0);
        if (!flipped) {
            mCamera.rotateY(mRotate);
        } else {
            mCamera.rotateY(180 - mRotate);
        }
        mCamera.translate(-centerX, -centerY, 0);
        mCamera.applyToCanvas(canvas);
        mCamera.restore();
        if (flipped) {
            mTo.draw(canvas);
        } else {
            mFrom.draw(canvas);
        }

    }

    @Override
    public Drawable getCurrent() {
        return mRotate > 90 ? mTo : mFrom;
    }

    /**
     * Starts the flip animation.
     * @param duration The duration in ms for the flip to occur.
     */
    public void start(long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 180);
        animator.setDuration(duration);
        animator.addUpdateListener(this);
        animator.start();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mRotate = (Float) animation.getAnimatedValue();
        invalidateSelf();
    }
}
