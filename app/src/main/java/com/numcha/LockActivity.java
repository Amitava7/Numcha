package com.numcha;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** PIN pad, plus the fingerprint prompt when that is switched on. */
public final class LockActivity extends Activity {

    private final StringBuilder entered = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout dots;
    private TextView message;
    private boolean promptedOnce;
    private CancellationSignal cancel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRecentsScreenshotEnabled(false);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "Numcha is locked", 26f, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.lp(Ui.MATCH, Ui.WRAP));
        message = Ui.text(this, "Enter your PIN", 15f, Ui.DIM, false);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        root.addView(message, Ui.lp(Ui.MATCH, Ui.WRAP));

        dots = Ui.row(this);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, Ui.lp(Ui.MATCH, Ui.dp(this, 28)));
        root.addView(Ui.gap(this, 24));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9",
                Lock.fingerprintOn(this) ? "Fingerprint" : "", "0", "Delete"};
        LinearLayout pad3 = Ui.column(this);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                final String key = keys[r * 3 + k];
                View v;
                if (key.isEmpty()) {
                    v = new View(this);
                } else {
                    boolean word = key.length() > 1;
                    Button b = Ui.button(this, key, word ? Ui.BG : Ui.PANEL,
                            word ? Ui.ACCENT : Ui.TEXT);
                    b.setTextSize(word ? 13f : 24f);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View x) {
                            press(key);
                        }
                    });
                    v = b;
                }
                LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 84), Ui.dp(this, 64));
                lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
                row.addView(v, lp);
            }
            pad3.addView(row);
        }
        root.addView(pad3);
        setContentView(root);
        Ui.fitSystemWindows(root);
        drawDots();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Lock.needsUnlock(this)) {
            finish();
            return;
        }
        if (!promptedOnce && Lock.fingerprintOn(this)) {
            promptedOnce = true;
            fingerprint();
        }
        showWait();
    }

    // onStop rather than onPause: on some phones the fingerprint dialog itself
    // pauses the activity underneath it.
    @Override
    protected void onStop() {
        super.onStop();
        promptedOnce = false;
        if (cancel != null) cancel.cancel();
        cancel = null;
    }

    @Override
    @SuppressWarnings("deprecation")  // still delivered: predictive back is not opted into
    public void onBackPressed() {
        // Backing out of the lock closes the app rather than revealing it.
        finishAffinity();
    }

    private void press(String key) {
        if (key.equals("Fingerprint")) {
            fingerprint();
            return;
        }
        if (key.equals("Delete")) {
            if (entered.length() > 0) entered.setLength(entered.length() - 1);
            drawDots();
            return;
        }
        if (Lock.waitMs(this) > 0) {
            showWait();
            return;
        }
        if (entered.length() >= Lock.MAX_PIN) return;
        entered.append(key);
        drawDots();
        if (entered.length() == Lock.pinLength(this)) {
            String pin = entered.toString();
            entered.setLength(0);
            if (Lock.check(this, pin)) {
                open();
            } else {
                drawDots();
                message.setText("Wrong PIN, try again");
                message.setTextColor(Ui.RED);
                showWait();
            }
        }
    }

    private void showWait() {
        long ms = Lock.waitMs(this);
        if (ms <= 0) return;
        message.setText("Too many tries. Wait " + ((ms + 999) / 1000) + " s");
        message.setTextColor(Ui.RED);
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(new Runnable() {
            public void run() {
                if (Lock.waitMs(LockActivity.this) > 0) {
                    showWait();
                } else {
                    message.setText("Enter your PIN");
                    message.setTextColor(Ui.DIM);
                }
            }
        }, 1000);
    }

    private void drawDots() {
        dots.removeAllViews();
        int n = Math.max(Lock.pinLength(this), entered.length());
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < entered.length()) g.setColor(Ui.ACCENT);
            else g.setStroke(Ui.dp(this, 2), Ui.DIM);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 16), Ui.dp(this, 16));
            lp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            dots.addView(d, lp);
        }
    }

    private void fingerprint() {
        if (!Lock.fingerprintAvailable(this)) {
            message.setText("No fingerprint set up on this phone. Use your PIN.");
            return;
        }
        cancel = new CancellationSignal();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Unlock Numcha")
                .setAllowedAuthenticators(Lock.AUTH)
                .setNegativeButton("Use PIN", getMainExecutor(), (d, w) -> { })
                .build();
        prompt.authenticate(cancel, getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        open();
                    }
                });
    }

    private void open() {
        Lock.unlocked();
        finish();
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
    }
}
