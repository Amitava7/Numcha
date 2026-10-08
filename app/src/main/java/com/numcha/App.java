package com.numcha;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Counts visible screens so the lock can close when the app leaves the screen. */
public final class App extends Application {

    private int started;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityStarted(Activity a) {
                started++;
            }

            public void onActivityStopped(Activity a) {
                started--;
                if (started <= 0 && !a.isChangingConfigurations()) {
                    started = 0;
                    Lock.wentToBackground();
                }
            }

            public void onActivityCreated(Activity a, Bundle b) {
            }

            public void onActivityResumed(Activity a) {
            }

            public void onActivityPaused(Activity a) {
            }

            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            public void onActivityDestroyed(Activity a) {
            }
        });
    }
}
