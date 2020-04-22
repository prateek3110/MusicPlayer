/* Created By : Prateek Sharma
 ******IIT(ISM) Dhanbad******/
package com.Prateek.Wrafter.WrafterMusic;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class CustomTouchListener implements RecyclerView.OnItemTouchListener {

    //Gesture detector to intercept the touch events
    GestureDetector gestureDetector;
    private com.Prateek.Wrafter.WrafterMusic.onItemClickListener clickListener;

    public CustomTouchListener(Context context, final com.Prateek.Wrafter.WrafterMusic.onItemClickListener clickListener) {
        this.clickListener = clickListener;
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent e) {

        View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)) {
            clickListener.onClick(child, recyclerView.getChildLayoutPosition(child));
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }
}
