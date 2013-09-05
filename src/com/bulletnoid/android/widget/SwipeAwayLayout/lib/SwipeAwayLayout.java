package com.bulletnoid.android.widget.SwipeAwayLayout.lib;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.Interpolator;
import android.widget.RelativeLayout;
import android.widget.Scroller;

/**
 * Created with IntelliJ IDEA.
 * User: bulletnoid
 * Date: 7/29/13
 * Time: 11:54 AM
 * To change this template use File | Settings | File Templates.
 */
public class SwipeAwayLayout extends RelativeLayout {

    private static final String TAG = "SwipeAwayRelativeLayout";
    private static final boolean DEBUG = false;

    public OnSwipeAwayListener mSwipeAwayListener;

    private Scroller mScroller;
    protected ColorDrawable mBgDrawable;

    int mPageMargin = 0;

    private boolean mAllowImtercept = true;
    private boolean mIsClose = false;

    protected static final int MAX_MENU_OVERLAY_ALPHA = 235;
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int CLOSE_ENOUGH = 2; // dp
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips

    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag;
    private int mTouchSlop;
    private int mCloseEnough;
    private float mInitialMotionX;
    /**
     * Position of the last motion event.
     */
    private float mLastMotionX;
    private float mLastMotionY;
    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;
    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    /**
     * Indicate the orientation this ViewGroup can be scrolled to.
     */
    public static final int LEFT_ONLY = 1;
    public static final int RIGHT_ONLY = LEFT_ONLY + 1;
    public static final int LEFT_RIGHT = RIGHT_ONLY + 1;
    public static int mSwipeOrientation = RIGHT_ONLY;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingDistance;

    /**
     * Indicates that the pager is in an idle, settled state. The current page
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that the pager is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that the pager is in the process of settling to a final position.
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    private int mScrollState = SCROLL_STATE_IDLE;

    public SwipeAwayLayout(Context context) {
        super(context);
        init(context);
    }

    public SwipeAwayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeAwayLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void init(Context context) {
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mScroller = new Scroller(context, sInterpolator);

        final float density = context.getResources().getDisplayMetrics().density;
        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
        mCloseEnough = (int) (CLOSE_ENOUGH * density);

        mBgDrawable = new ColorDrawable(0xFF000000);

    }

    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    public interface OnSwipeAwayListener {
        public void onSwipedAway();
    }

    public void setOnSwipeAwayListener(OnSwipeAwayListener listener) {
        this.mSwipeAwayListener = listener;
    }

    public void setSwipeOrientation(int ori) {
        mSwipeOrientation = ori;
    }

    public void setAllowIntercept(boolean value) {
        mAllowImtercept = value;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        if (!mAllowImtercept) {
            return false;
        }

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        // Always take care of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the drag.
            mIsBeingDragged = false;
            mIsUnableToDrag = false;
            mActivePointerId = INVALID_POINTER;
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            return false;
        }

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsBeingDragged) {
                if (DEBUG) Log.v(TAG, "Intercept returning true!");
                return true;
            }
            if (mIsUnableToDrag) {
                if (DEBUG) Log.v(TAG, "Intercept returning false!");
                return false;
            }
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionY is set to the y value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float dx = x - mLastMotionX;
                final float xDiff;
                if (mSwipeOrientation == LEFT_ONLY) {
                    xDiff = -dx;
                } else if (mSwipeOrientation == RIGHT_ONLY) {
                    xDiff = dx;
                } else {
                    xDiff = Math.abs(dx);
                }
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = Math.abs(y - mLastMotionY);
                if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);

                if (canScroll(this, false, (int) dx, (int) x, (int) y)) {
                    // Nested view has scrollable area under this point. Let it be handled there.
                    mInitialMotionX = mLastMotionX = x;
                    mLastMotionY = y;
                    return false;
                }
                if (xDiff > mTouchSlop && xDiff > yDiff) {
                    if (DEBUG) Log.v(TAG, "Starting drag!");
                    mIsBeingDragged = true;
                    setScrollState(SCROLL_STATE_DRAGGING);
                    mLastMotionX = x;
                } else {
                    if (yDiff > mTouchSlop) {
                        // The finger has moved enough in the vertical
                        // direction to be counted as a drag...  abort
                        // any attempt to drag horizontally, to work correctly
                        // with children that have scrolling containers.
                        if (DEBUG) Log.v(TAG, "Starting unable to drag!");
                        mIsUnableToDrag = true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);

                if (mScrollState == SCROLL_STATE_SETTLING &&
                        Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) > mCloseEnough) {
                    // Let the user 'catch' the pager as it animates.
                    mIsBeingDragged = true;
                    mIsUnableToDrag = false;
                    setScrollState(SCROLL_STATE_DRAGGING);
                } else {
//                    completeScroll();
                    mIsBeingDragged = false;
                    mIsUnableToDrag = false;
                }

                if (DEBUG) Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
                        + " mIsBeingDragged=" + mIsBeingDragged
                        + "mIsUnableToDrag=" + mIsUnableToDrag);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (!mIsBeingDragged) {
            // Track the velocity as long as we aren't dragging.
            // Once we start a real drag we will track in onTouchEvent.
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityTracker.obtain();
            }
            mVelocityTracker.addMovement(ev);
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants.
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        boolean needsInvalidate = false;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
//                completeScroll();

                // Remember where the motion event started
                mLastMotionX = mInitialMotionX = ev.getX();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (!mIsBeingDragged) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float xDiff = Math.abs(x - mLastMotionX);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float yDiff = Math.abs(y - mLastMotionY);
                    if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                    if (xDiff > mTouchSlop && xDiff > yDiff) {
                        if (DEBUG) Log.v(TAG, "Starting drag!");
                        mIsBeingDragged = true;
                        mLastMotionX = x;
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
                    final float deltaX = mLastMotionX - x;
                    mLastMotionX = x;
                    float oldScrollX = getScrollX();
                    float scrollX = oldScrollX + deltaX;
                    final int width = getWidth();
                    final int widthWithMargin = width + mPageMargin;

//                    final int lastItemIndex = mAdapter.getCount() - 1;
//                    final float leftBound = Math.max(0, (mCurItem - 1) * widthWithMargin);
//                    final float rightBound =
//                            Math.min(mCurItem + 1, lastItemIndex) * widthWithMargin;
//                    if (scrollX < leftBound) {
//                        if (leftBound == 0) {
//                            float over = -scrollX;
//                            needsInvalidate = mLeftEdge.onPull(over / width);
//                        }
//                        scrollX = leftBound;
//                    } else if (scrollX > rightBound) {
//                        if (rightBound == lastItemIndex * widthWithMargin) {
//                            float over = scrollX - rightBound;
//                            needsInvalidate = mRightEdge.onPull(over / width);
//                        }
//                        scrollX = rightBound;
//                    }
                    // Don't lose the rounded component
                    mLastMotionX += scrollX - (int) scrollX;

                    if (mSwipeOrientation == RIGHT_ONLY) {
                        scrollX = Math.min(0, scrollX);
                    } else if (mSwipeOrientation == LEFT_ONLY) {
                        scrollX = Math.max(0, scrollX);
                    } else {
                    }

                    scrollTo((int) scrollX, getScrollY());
                    mBgDrawable.setAlpha(getRatioAlpha((int) scrollX, width));
                    setBackgroundDrawable(mBgDrawable);
//                    pageScrolled((int) scrollX);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(
                            velocityTracker, mActivePointerId);
                    final int widthWithMargin = getWidth() + mPageMargin;
                    final int scrollX = getScrollX();
                    final float pageOffset = (float) (scrollX % widthWithMargin) / widthWithMargin;
                    final int activePointerIndex =
                            MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
                    final int totalDelta = (int) (x - mInitialMotionX);
                    int destX = 0;

                    if (mSwipeOrientation == RIGHT_ONLY) {
                        if (totalDelta >= widthWithMargin / 2) {
                            destX = -widthWithMargin;
                            mIsClose = true;
                        } else if (totalDelta >= widthWithMargin / 4) {
                            if (initialVelocity >= 1000) {
                                destX = -widthWithMargin;
                                mIsClose = true;
                            }
                        } else {
                            destX = 0;
                            mIsClose = false;
                        }
                    } else if (mSwipeOrientation == LEFT_ONLY) {
                        if (totalDelta <= -widthWithMargin / 2) {
                            destX = widthWithMargin;
                            mIsClose = true;
                        } else if (totalDelta <= -widthWithMargin / 4) {
                            if (initialVelocity >= 1000) {
                                destX = widthWithMargin;
                                mIsClose = true;
                            }
                        } else {
                            destX = 0;
                            mIsClose = false;
                        }
                    } else {
                        int totalAbs = Math.abs(totalDelta);
                        if (totalAbs >= widthWithMargin / 2) {
                            destX = -widthWithMargin * totalAbs / totalDelta;
                            mIsClose = true;
                        } else if (totalAbs >= widthWithMargin / 4) {
                            if (initialVelocity >= 1000) {
                                destX = -widthWithMargin * totalAbs / totalDelta;
                                mIsClose = true;
                            }
                        } else {
                            destX = 0;
                            mIsClose = false;
                        }
                    }

                    smoothScrollTo(destX, 0, initialVelocity);

                    mActivePointerId = INVALID_POINTER;
                    endDrag();
//                    needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged) {
//                    setCurrentItemInternal(mCurItem, true, true);
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
//                    needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
                }
                break;
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                final float x = MotionEventCompat.getX(ev, index);
                mLastMotionX = x;
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = MotionEventCompat.getX(ev,
                        MotionEventCompat.findPointerIndex(ev, mActivePointerId));
                break;
        }
        if (needsInvalidate) {
            invalidate();
        }
        return true;
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v      View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx     Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }


    private void setScrollState(int newState) {
        if (mScrollState == newState) {
            return;
        }

        mScrollState = newState;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;
        mIsUnableToDrag = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    void smoothScrollTo(int x, int y, int velocity) {
        int sx = getScrollX();
        int sy = getScrollY();
        int dx = x - sx;
        int dy = y - sy;
        if (dx == 0 && dy == 0) {
//            completeScroll();
            setScrollState(SCROLL_STATE_IDLE);
            return;
        }

//        setScrollingCacheEnabled(true);
        setScrollState(SCROLL_STATE_SETTLING);

        final int width = getWidth();
//        final int halfWidth = width / 2;
//        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);

        float distance = 0;
        if (x == 0) {
            distance = (width + mPageMargin) - getScrollX();
        } else {
            distance = getScrollX() - (width + mPageMargin);
        }
        distance = Math.abs(distance);

        int duration = 0;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float pageDelta = (float) Math.abs(dx) / (width + mPageMargin);
            duration = (int) ((pageDelta + 1) * 100);
        }
//        duration = Math.min(duration, MAX_SETTLE_DURATION);
        duration = 200;

        mScroller.startScroll(sx, sy, dx, dy, duration);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                scrollTo(x, y);
                mBgDrawable.setAlpha(getRatioAlpha(x, getWidth()));
                setBackgroundDrawable(mBgDrawable);
            }

            // Keep on drawing until the animation has finished.
            ViewCompat.postInvalidateOnAnimation(this);
            return;
        }

        if (mIsClose) {
            mSwipeAwayListener.onSwipedAway();
        }

        // Done with scroll, clean up state.
//        completeScroll(true);
    }

    public int getRatioAlpha(int scrollX, int width) {
        return MAX_MENU_OVERLAY_ALPHA - MAX_MENU_OVERLAY_ALPHA * Math.abs(scrollX) / width;
    }

}
