/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scottrosenquist.scratchpadwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class ScratchpadWatchface extends CanvasWatchFaceService {

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ScratchpadWatchface.Engine> engineWeakReference;

        public EngineHandler(ScratchpadWatchface.Engine reference) {
            engineWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            ScratchpadWatchface.Engine engine = engineWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 10f;
        private static final float MINUTE_STROKE_WIDTH = 7f;
        private static final float SECOND_STROKE_WIDTH = 4f;
        private static final float CENTER_STROKE_WIDTH = 3.5f;
        private static final float TICK_STROKE_WIDTH = 1.5f;

        private static final float CENTER_CIRCLE_RADIUS = 8f;
        private static final float HAND_CENTER_GAP_RADIUS = 15f;

//        private static final int SHADOW_RADIUS = 6;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean registeredTimeZoneReceiver = false;
//        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float mSecondHandStubLength;
        private float mMinuteHandLength;
        private float mHourHandLength;
        private float mtickRadius;
        private float mNumberRadius;
//        private float
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
//        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mCirclePaint;
        private Paint mCircleAmbientPaint;
        private Paint mTickPaint;
//        private float mNumberTextSize = 24f;
        private Paint mNumberPaint;
        private Paint mBoxPaint;
        private Paint notificationIndicatorPaint;
//        private Paint mBackgroundPaint;
        private int mBackgroundColor;
        private boolean ambient;
        private boolean lowBitAmbient;
        private boolean mBurnInProtection;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            WatchHand hourHand = new WatchHand();



            setWatchFaceStyle(new WatchFaceStyle.Builder(ScratchpadWatchface.this)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
//                    WatchFaceStyle.PROTECT_STATUS_BAR | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR
                    .setShowUnreadCountIndicator(true)

                    .setStatusBarGravity(Gravity.CENTER_VERTICAL)
//                    .setAcceptsTapEvents(true)
                    .build());

//            mBackgroundPaint = new Paint();
//            mBackgroundPaint.setColor(Color.RED);
            mBackgroundColor = Color.parseColor("#211E1D");


            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.GRAY;
//            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.BUTT);
//            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.BUTT);
//            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.BUTT);
//            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mCirclePaint = new Paint();
            mCirclePaint.setColor(mWatchHandColor);
//            mCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStyle(Paint.Style.FILL);

            mCircleAmbientPaint = new Paint();
            mCircleAmbientPaint.setColor(Color.BLACK);
            mCircleAmbientPaint.setAntiAlias(true);
            mCircleAmbientPaint.setStyle(Paint.Style.FILL);
//            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickPaint = new Paint();
            mTickPaint.setColor(mWatchHandColor);
//            mTickPaint.setStrokeWidth(TICK_STROKE_WIDTH);
            mTickPaint.setAntiAlias(true);
            mTickPaint.setStyle(Paint.Style.FILL);
//            mTickPaint.setTextAlign(Paint.Align.CENTER);

            mNumberPaint = new Paint();
            mNumberPaint.setColor(mWatchHandColor);
//            mNumberPaint.setStrokeWidth(TICK_STROKE_WIDTH);
            mNumberPaint.setTextSize(36f);
            mNumberPaint.setAntiAlias(true);
            mNumberPaint.setStyle(Paint.Style.FILL);
            mNumberPaint.setTextAlign(Paint.Align.LEFT);

            mBoxPaint = new Paint();
            mBoxPaint.setColor(mWatchHandColor);
            mBoxPaint.setStyle(Paint.Style.STROKE);
//            mBoxPaint.set
            mBoxPaint.setStrokeWidth(1f);

            notificationIndicatorPaint = new Paint();
            notificationIndicatorPaint.setColor(mWatchHandColor);
            notificationIndicatorPaint.setStyle(Paint.Style.FILL);
            notificationIndicatorPaint.setAntiAlias(true);
//            getNotificationCount()

            calendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            Log.d("debug","lowBitAmbient: "+ lowBitAmbient);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            Log.d("debug","mBurnInProtection: "+mBurnInProtection);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            ambient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (ambient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mCirclePaint.setColor(Color.WHITE);
                mCircleAmbientPaint.setColor(Color.BLACK);
                mTickPaint.setColor(Color.WHITE);
                mNumberPaint.setColor(Color.WHITE);

//                if (lowBitAmbient) {
                mHourPaint.setAntiAlias(!lowBitAmbient);
                mMinutePaint.setAntiAlias(!lowBitAmbient);
                mSecondPaint.setAntiAlias(!lowBitAmbient);
                mCirclePaint.setAntiAlias(!lowBitAmbient);
                mCircleAmbientPaint.setAntiAlias(!lowBitAmbient);
                mTickPaint.setAntiAlias(!lowBitAmbient);
                mNumberPaint.setAntiAlias(!lowBitAmbient);

//                mTickPaint.setStyle(Paint.Style.STROKE);

//                mHourPaint.clearShadowLayer();
//                mMinutePaint.clearShadowLayer();
//                mSecondPaint.clearShadowLayer();
//                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mCirclePaint.setColor(mWatchHandColor);
                mCircleAmbientPaint.setColor(mBackgroundColor);
                mTickPaint.setColor(mWatchHandColor);
                mNumberPaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mCirclePaint.setAntiAlias(true);
                mTickPaint.setAntiAlias(true);
                mNumberPaint.setAntiAlias(true);

//                mTickPaint.setStyle(Paint.Style.FILL);

//                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
//                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
//                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
//                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        // Who would want this???
//        @Override
//        public void onInterruptionFilterChanged(int interruptionFilter) {
//            super.onInterruptionFilterChanged(interruptionFilter);
//            Log.d("debug", "onInterruptionFilterChanged("+interruptionFilter+")");
//
////            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
//            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_PRIORITY);
//            // Who would want this???
//
//            /* Dim display in mute mode. */
//            if (mMuteMode != inMuteMode) {
//                mMuteMode = inMuteMode;
////                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
////                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
////                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
//                mHourPaint.setColor(Color.RED);
//                mMinutePaint.setColor(Color.GREEN);
//                mSecondPaint.setColor(Color.BLUE);
//                invalidate();
//            }
//        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d("debug","onSurfaceChanged()");

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.8);
            mSecondHandStubLength = (float) (mCenterX * 0.18);
            mMinuteHandLength = (float) (mCenterX * 0.7);
            Log.d("debug","mMinuteHandLength: "+(mMinuteHandLength - HAND_CENTER_GAP_RADIUS));
            mHourHandLength = (float) (mCenterX * 0.5);
            Log.d("debug","mHourHandLength: "+(mHourHandLength - HAND_CENTER_GAP_RADIUS));
            mtickRadius = (float) (mCenterX * 0.9);
            mNumberRadius = (float) (mCenterX * 0.85);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d("debug","onTapCommand(), tapType: "+tapType);
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
                    break;
            }
//            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);

            if (ambient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(mBackgroundColor);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
//            float innerTickRadius = mCenterX - 16;
//            float outerTickRadius = mCenterX - 6;
//            float tickRadius = mCenterX - 12;
            float tickSize = 5f; // 7f max size for screen burn in
            float boxSize = 10f;
            for (int tickIndex = 1; tickIndex <= 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
//                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
//                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
//                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
//                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                float tickX = (float) Math.sin(tickRot) * mtickRadius;
                float numX = (float) Math.sin(tickRot) * mNumberRadius;

                float tickY = (float) -Math.cos(tickRot) * mtickRadius;

                Log.d("debug","tickIndex(x,y): "+tickIndex+"("+tickX+","+tickY+")");
//                Log.d("debug","tickX: "+ tickX);
//                Log.d("debug","tickY: "+ tickY);
                float numY = (float) -Math.cos(tickRot) * mNumberRadius;

//                + (mNumberTextSize/2f)
//                 - ((textPaint.descent() + textPaint.ascent()) / 2)
//                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
//                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
                String tickString = String.valueOf(tickIndex);
                Rect textBounds = new Rect();
                mNumberPaint.getTextBounds(tickString, 0, tickString.length(), textBounds);
                Log.d("debug","textBounds.bottom: "+textBounds.bottom);
                float numberX;
                float numberY;

//                mNumberPaint.setColor(mWatchHandColor);

                switch (tickIndex) {
                    case 12:
                        //top
                        numberX = tickX - textBounds.width() / 2f - textBounds.left;
//                        numberY = tickY + textBounds.height() / 2f - textBounds.bottom;
                        numberY = tickY + textBounds.height() - textBounds.bottom;
                        canvas.drawText(String.valueOf(tickIndex), mCenterX + numberX, mCenterX + numberY, mNumberPaint);
//                        canvas.drawCircle(mCenterX + tickX, mCenterX + tickY, tickSize, mTickPaint);
                        break;
                    case 3:
                        //right
//                        numberX = tickX - textBounds.width() / 2f - textBounds.left;
                        numberX = tickX - textBounds.width() - textBounds.left;
                        numberY = tickY + textBounds.height() / 2f - textBounds.bottom;
                        canvas.drawText(String.valueOf(tickIndex), mCenterX + numberX, mCenterX + numberY, mNumberPaint);
//                        canvas.drawCircle(mCenterX + tickX, mCenterX + tickY, tickSize, mTickPaint);
                        break;
                    case 6:
                        //bottom
                        numberX = tickX - textBounds.width() / 2f - textBounds.left;
//                        numberY = tickY + textBounds.height() / 2f - textBounds.bottom;
                        numberY = tickY - textBounds.bottom;
                        if (getNotificationCount() > 0) {
                            notificationIndicatorPaint.setColor(mWatchHandColor);
                            canvas.drawCircle(mCenterX + tickX, mCenterX + tickY - textBounds.height() / 2f , 25f, notificationIndicatorPaint);
                            if (ambient) {
                                notificationIndicatorPaint.setColor(Color.BLACK);

                            } else {
                                notificationIndicatorPaint.setColor(mBackgroundColor);
                            }
                            canvas.drawCircle(mCenterX + tickX, mCenterX + tickY - textBounds.height() / 2f , 20f, notificationIndicatorPaint);
//                            mNumberPaint.setColor(mBackgroundColor);
                        } else {
//                            mNumberPaint.setColor(mWatchHandColor);
                        }
                        canvas.drawText(String.valueOf(tickIndex), mCenterX + numberX, mCenterX + numberY, mNumberPaint);
//                        canvas.drawCircle(mCenterX + tickX, mCenterX + tickY, tickSize, mTickPaint);
                        break;
                    case 9:
                        //left
//                        numberX = tickX - textBounds.width() / 2f - textBounds.left;
                        numberX = tickX - textBounds.left;
                        numberY = tickY + textBounds.height() / 2f - textBounds.bottom;
                        canvas.drawText(String.valueOf(tickIndex), mCenterX + numberX, mCenterX + numberY, mNumberPaint);
//                        canvas.drawCircle(mCenterX + tickX, mCenterX + tickY, tickSize, mTickPaint);
                        break;

//                        canvas.drawText(String.valueOf(tickIndex), mCenterX + numberX, mCenterX + numberY, mNumberPaint);
//                        canvas.drawRect(mCenterX + tickX + boxSize, mCenterX + tickY + boxSize, mCenterX + tickX - boxSize, mCenterX + tickY - boxSize, mBoxPaint);
//                        canvas.drawRect(mCenterX + tickX, mCenterX + tickY, mCenterX + tickX - boxSize, mCenterX + tickY - boxSize, mBoxPaint);
//                        canvas.drawRect(mCenterX + tickX + boxSize, mCenterX + tickY + boxSize, mCenterX + tickX, mCenterX + tickY, mBoxPaint);
//                        break;
                    default:
                        canvas.drawCircle(mCenterX + tickX, mCenterX + tickY, tickSize, mTickPaint);
                        break;
                }
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = calendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = calendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (calendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!ambient) {
//                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.rotate(secondsRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - HAND_CENTER_GAP_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

                canvas.drawLine(
                        mCenterX,
                        mCenterY + HAND_CENTER_GAP_RADIUS,
                        mCenterX,
                        mCenterY + mSecondHandStubLength,
                        mSecondPaint);
                canvas.rotate(hoursRotation - secondsRotation, mCenterX, mCenterY);
            } else {
                canvas.rotate(hoursRotation, mCenterX, mCenterY);
            }

//            canvas.rotate(hoursRotation - secondsRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - HAND_CENTER_GAP_RADIUS,
                    mCenterX,
                    mCenterY - mHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - HAND_CENTER_GAP_RADIUS,
                    mCenterX,
                    mCenterY - mMinuteHandLength,
                    mMinutePaint);

            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_CIRCLE_RADIUS,
                    mCirclePaint);

            if (ambient) {
                canvas.drawCircle(mCenterX, mCenterY, CENTER_CIRCLE_RADIUS - CENTER_STROKE_WIDTH, mCircleAmbientPaint);
            }

            /* Restore the canvas' original orientation. */
            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d("debug","onVisibilityChanged, visible: "+visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ScratchpadWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            ScratchpadWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !ambient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
