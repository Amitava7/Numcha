package com.numcha;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Every journal screen extends this. If the lock is on and not yet opened, the
 * screen hides itself and puts the lock screen in front.
 */
abstract class Base extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Keep the journal out of the recent-apps thumbnail when it is locked.
        setRecentsScreenshotEnabled(!Lock.pinOn(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Lock.cameBack();
        boolean locked = Lock.needsUnlock(this);
        getWindow().getDecorView().setVisibility(locked ? android.view.View.INVISIBLE
                : android.view.View.VISIBLE);
        if (locked) {
            startActivity(new Intent(this, LockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Lock.needsUnlock(this)) {
            getWindow().getDecorView().setVisibility(android.view.View.VISIBLE);
            setRecentsScreenshotEnabled(!Lock.pinOn(this));
        }
    }

    /** Opens another app (camera, picker) without that counting as leaving. */
    void startAway(Intent i, int request) {
        Lock.stepAway();
        startActivityForResult(i, request);
    }
}
