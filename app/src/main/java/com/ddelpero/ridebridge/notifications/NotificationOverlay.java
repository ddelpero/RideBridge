package com.ddelpero.ridebridge.notifications;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import com.ddelpero.ridebridge.R;

public class NotificationOverlay extends FrameLayout {
    
    private static final String TAG = "RideBridge_NotifOverlay";
    private static final float SWIPE_THRESHOLD = 100f;
    
    private ImageView appIcon;
    private TextView senderName;
    private TextView messageText;
    private NotificationData currentNotification;
    private float startX;
    private OnDismissListener onDismissListener;
    
    public interface OnDismissListener {
        void onDismiss();
    }
    
    public NotificationOverlay(Context context) {
        super(context);
        init();
    }
    
    public NotificationOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public NotificationOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        inflate(getContext(), R.layout.notification_overlay, this);
        appIcon = findViewById(R.id.notif_app_icon);
        senderName = findViewById(R.id.notif_sender);
        messageText = findViewById(R.id.notif_message);
        
        setOnTouchListener(this::handleTouch);
    }
    
    public void showNotification(NotificationData data) {
        this.currentNotification = data;
        
        senderName.setText(data.sender);
        messageText.setText(data.message);
        
        // Set app icon based on package
        setAppIcon(data.appPackage);
        
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        animate().alpha(1f).setDuration(300).start();
        
        Log.d(TAG, "Showing notification: " + data.sender + " - " + data.message);
        
        // Auto-dismiss after 5 seconds
        postDelayed(this::dismissNotification, 5000);
    }
    
    private void setAppIcon(String appPackage) {
        try {
            Drawable icon = getContext().getPackageManager().getApplicationIcon(appPackage);
            appIcon.setImageDrawable(icon);
        } catch (Exception e) {
            Log.e(TAG, "Error loading app icon: " + e.getMessage());
            // Use default icon - circle_white as placeholder
            appIcon.setImageResource(R.drawable.circle_white);
        }
    }
    
    private boolean handleTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - startX;
                setTranslationX(deltaX);
                setAlpha(1f - Math.abs(deltaX) / getWidth());
                return true;
                
            case MotionEvent.ACTION_UP:
                float deltaX_final = event.getX() - startX;
                if (Math.abs(deltaX_final) > SWIPE_THRESHOLD) {
                    dismissNotification();
                } else {
                    // Snap back
                    animate().translationX(0f).alpha(1f).setDuration(200).start();
                }
                return true;
        }
        return false;
    }
    
    public void dismissNotification() {
        removeCallbacks(this::dismissNotification);
        animate().alpha(0f).translationX(getWidth()).setDuration(300)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                    setTranslationX(0f);
                    setAlpha(1f);
                    if (onDismissListener != null) {
                        onDismissListener.onDismiss();
                    }
                }
            }).start();
    }
    
    public void setOnDismissListener(OnDismissListener listener) {
        this.onDismissListener = listener;
    }
}
