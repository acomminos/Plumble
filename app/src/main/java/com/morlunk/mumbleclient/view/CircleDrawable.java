package com.morlunk.mumbleclient.view;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

import com.morlunk.mumbleclient.R;

/**
 * A drawable containing a circular bitmap in the style of @drawable/outline_circle_talking_off.
 * Created by andrew on 19/10/14.
 */
public class CircleDrawable extends BitmapDrawable {
    public static final int STROKE_WIDTH_DP = 1;
    private Paint mPaint;
    private Paint mBorderPaint;
    private int mStrokeWidth;
    private int mWidth;
    private int mHeight;

    public CircleDrawable(Resources res, Bitmap bitmap) {
        super(res, bitmap);
        mPaint = new Paint();
        mPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        mPaint.setDither(true);
        mPaint.setAntiAlias(true);
        mStrokeWidth = (int) (res.getDisplayMetrics().density * STROKE_WIDTH_DP);
        mBorderPaint = new Paint();
        mBorderPaint.setColor(res.getColor(R.color.ripple_talk_state_disabled));
        mBorderPaint.setStrokeWidth(mStrokeWidth);
        mBorderPaint.setDither(true);
        mBorderPaint.setAntiAlias(true);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mWidth = bounds.width();
        mHeight = bounds.height();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawCircle(mWidth / 2, mHeight / 2, mWidth / 2, mBorderPaint);
        canvas.drawCircle(mWidth / 2, mHeight / 2, mWidth/2 - mStrokeWidth, mPaint);
    }
}
