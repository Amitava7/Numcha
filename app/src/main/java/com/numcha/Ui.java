package com.numcha;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours, a few view factories, and window-inset handling. No XML layouts. */
final class Ui {

    static final int BG = 0xFF161B22;
    static final int PANEL = 0xFF1F2630;
    static final int BUTTON = 0xFF2D3642;   // reads as a button against PANEL
    static final int EMPTY = 0xFF2A323D;    // a day with nothing written
    static final int PLAIN = 0xFF56606D;    // a day written, but given no colour
    static final int TEXT = 0xFFE6EDF3;
    static final int DIM = 0xFF8B949E;
    static final int ACCENT = 0xFF58A6FF;

    static final int GREEN = 0xFF2FBF71;
    static final int YELLOW = 0xFFF2C94C;
    static final int RED = 0xFFE5544B;
    static final int BLACK = 0xFF000000;
    static final int BLACK_EDGE = 0xFF8B949E; // so a black day shows on a dark page

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        b.setTextColor(fg);
        b.setBackground(pill(bg));
        b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        b.setStateListAnimator(null);
        b.setMinimumHeight(dp(c, 48));
        b.setMinimumWidth(0);
        b.setMinWidth(0);
        return b;
    }

    static EditText field(Context c, String hint, float sizeSp) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadius(dp(c, 10));
        e.setBackground(g);
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        return e;
    }

    private static StateListDrawable pill(int bg) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, round(dim(bg)));
        sl.addState(new int[]{-android.R.attr.state_enabled}, round(dim(bg)));
        sl.addState(new int[0], round(bg));
        return sl;
    }

    private static GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(999f);
        return g;
    }

    static GradientDrawable card(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadius(dp(c, 12));
        return g;
    }

    private static int dim(int c) {
        return Color.argb(Color.alpha(c), Color.red(c) * 2 / 3, Color.green(c) * 2 / 3,
                Color.blue(c) * 2 / 3);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(lp(dp(c, dp), dp(c, dp)));
        return v;
    }

    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    /**
     * Apps targeting Android 15 draw behind the system bars, so pad the root
     * view by whatever the bars and the keyboard cover.
     */
    static void fitSystemWindows(final View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(l + bars.left, t + bars.top, r + bars.right,
                        b + Math.max(bars.bottom, ime.bottom));
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    static void hideKeyboard(Activity a, View v) {
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    static void showKeyboard(Activity a, View v) {
        v.requestFocus();
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }
}
