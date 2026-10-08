package com.numcha;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.Locale;

/** App lock, and moving the journal in and out through a file. */
public final class SettingsActivity extends Base {

    private static final int REQ_EXPORT = 1;
    private static final int REQ_IMPORT = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Switch pinSwitch, fingerSwitch;
    private Button changePin;
    private TextView fingerNote, dataSummary;
    private boolean quiet;     // true while we set switches ourselves
    private AlertDialog working;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.text(this, "Settings", 26f, Ui.TEXT, true));

        LinearLayout body = Ui.column(this);
        body.setPadding(0, Ui.dp(this, 12), 0, 0);

        // ---- lock
        LinearLayout lock = section(body, "App lock");
        pinSwitch = toggle(lock, "Lock with PIN");
        pinSwitch.setOnCheckedChangeListener((b, on) -> {
            if (quiet) return;
            if (on) choosePin(false);
            else askPin("Turn the lock off", "Enter your current PIN.", () -> {
                Lock.clearPin(this);
                showLock();
            });
        });
        changePin = Ui.button(this, "Change PIN", Ui.BUTTON, Ui.TEXT);
        changePin.setOnClickListener(v -> askPin("Change PIN", "Enter your current PIN.",
                () -> choosePin(true)));
        LinearLayout.LayoutParams cp = Ui.lp(Ui.MATCH, Ui.WRAP);
        cp.topMargin = Ui.dp(this, 6);
        lock.addView(changePin, cp);
        fingerSwitch = toggle(lock, "Unlock with fingerprint");
        fingerSwitch.setOnCheckedChangeListener((b, on) -> {
            if (quiet) return;
            if (on) confirmFingerprint();
            else {
                Lock.setFingerprint(this, false);
                showLock();
            }
        });
        fingerNote = note(lock, "");
        note(lock, "The lock shows whenever Numcha is opened or comes back from the "
                + "background. There is no way to reset a forgotten PIN without clearing "
                + "the app's data, which deletes the journal, so keep an export.");

        // ---- data
        LinearLayout data = section(body, "Your data");
        dataSummary = note(data, "");
        Button exp = Ui.button(this, "Export to a file", Ui.BUTTON, Ui.TEXT);
        exp.setOnClickListener(v -> startExport());
        Button imp = Ui.button(this, "Import from a file", Ui.BUTTON, Ui.TEXT);
        imp.setOnClickListener(v -> startImport());
        LinearLayout.LayoutParams bl = Ui.lp(Ui.MATCH, Ui.WRAP);
        bl.topMargin = Ui.dp(this, 8);
        data.addView(exp, bl);
        LinearLayout.LayoutParams bl2 = Ui.lp(Ui.MATCH, Ui.WRAP);
        bl2.topMargin = Ui.dp(this, 8);
        data.addView(imp, bl2);
        note(data, "The export is a .zip with every post and picture. Save it anywhere - "
                + "Downloads, a USB stick, your own cloud drive - and import it on this phone "
                + "or a new one. Importing adds posts and updates ones it already has; it "
                + "can also replace the whole journal.");

        LinearLayout about = section(body, "Privacy");
        note(about, "Everything is stored only on this phone, in Numcha's private storage. "
                + "The app has no internet permission and is excluded from cloud backup, "
                + "so nothing leaves the phone unless you export it.");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f));
        setContentView(root);
        Ui.fitSystemWindows(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showLock();
        showData();
    }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.card(this));
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        card.addView(Ui.text(this, title, 19f, Ui.ACCENT, true));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.bottomMargin = Ui.dp(this, 14);
        parent.addView(card, lp);
        return card;
    }

    private Switch toggle(LinearLayout parent, String label) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(16f);
        s.setMinHeight(Ui.dp(this, 52));
        parent.addView(s, Ui.lp(Ui.MATCH, Ui.WRAP));
        return s;
    }

    private TextView note(LinearLayout parent, String s) {
        TextView t = Ui.text(this, s, 14f, Ui.DIM, false);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        parent.addView(t);
        return t;
    }

    private void showLock() {
        quiet = true;
        boolean pin = Lock.pinOn(this);
        boolean can = Lock.fingerprintAvailable(this);
        pinSwitch.setChecked(pin);
        changePin.setVisibility(pin ? View.VISIBLE : View.GONE);
        fingerSwitch.setChecked(Lock.fingerprintOn(this));
        fingerSwitch.setEnabled(pin && can);
        fingerSwitch.setTextColor(pin && can ? Ui.TEXT : Ui.DIM);
        fingerNote.setText(!can ? "No fingerprint is set up in the phone's settings."
                : !pin ? "Turn on the PIN first: it is the way in if the fingerprint fails."
                : "Either your fingerprint or the PIN opens the journal.");
        quiet = false;
        setRecentsScreenshotEnabled(!pin);
    }

    private void showData() {
        new Thread(() -> {
            Store s = Store.get(getApplicationContext());
            final int n = s.count(), ph = s.photoCount();
            final long bytes = s.photoBytes();
            ui.post(() -> dataSummary.setText(String.format(Locale.getDefault(),
                    "%d posts, %d pictures (%.1f MB)", n, ph, bytes / 1048576.0)));
        }, "summary").start();
    }

    // ---- PIN dialogs ---------------------------------------------------------

    private EditText pinField() {
        EditText e = Ui.field(this, "PIN", 22f);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(Lock.MAX_PIN)});
        e.setSingleLine(true);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        return e;
    }

    /** Lets the keyboard's Done key press the dialog's main button. */
    private static void doneClicks(EditText field, final Button ok) {
        field.setOnEditorActionListener((v, action, event) -> {
            ok.performClick();
            return true;
        });
    }

    private LinearLayout dialogBody(TextView msg, EditText field) {
        LinearLayout box = Ui.column(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(msg);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.topMargin = Ui.dp(this, 12);
        box.addView(field, lp);
        return box;
    }

    interface Then {
        void run();
    }

    /** Asks for the current PIN, then runs {@code then}. Cancelling restores the switches. */
    private void askPin(String title, String message, final Then then) {
        final EditText field = pinField();
        final TextView msg = Ui.text(this, message, 15f, Ui.DIM, false);
        final AlertDialog d = new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(dialogBody(msg, field))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", null)
                .create();
        d.setOnDismissListener(x -> showLock());
        d.setOnShowListener(x -> {
            Ui.showKeyboard(this, field);
            doneClicks(field, d.getButton(AlertDialog.BUTTON_POSITIVE));
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long wait = Lock.waitMs(this);
                if (wait > 0) {
                    msg.setText("Too many tries. Wait " + ((wait + 999) / 1000) + " s.");
                    msg.setTextColor(Ui.RED);
                    return;
                }
                if (Lock.check(this, field.getText().toString())) {
                    d.dismiss();
                    then.run();
                } else {
                    field.setText("");
                    msg.setText("That is not the PIN.");
                    msg.setTextColor(Ui.RED);
                }
            });
        });
        d.show();
    }

    /** Two steps: choose a PIN, then type it again. */
    private void choosePin(final boolean changing) {
        final EditText field = pinField();
        final TextView msg = Ui.text(this, "Choose a PIN of " + Lock.MIN_PIN + " to "
                + Lock.MAX_PIN + " digits.", 15f, Ui.DIM, false);
        final String[] first = {null};
        final AlertDialog d = new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(changing ? "New PIN" : "Set a PIN")
                .setView(dialogBody(msg, field))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", null)
                .create();
        d.setOnDismissListener(x -> showLock());
        d.setOnShowListener(x -> {
            Ui.showKeyboard(this, field);
            final Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
            doneClicks(field, ok);
            ok.setOnClickListener(v -> {
                String s = field.getText().toString();
                if (first[0] == null) {
                    if (!Lock.validPin(s)) {
                        msg.setText("Use " + Lock.MIN_PIN + " to " + Lock.MAX_PIN + " digits.");
                        msg.setTextColor(Ui.RED);
                        return;
                    }
                    first[0] = s;
                    field.setText("");
                    msg.setText("Type the same PIN again.");
                    msg.setTextColor(Ui.DIM);
                    ok.setText("Set PIN");
                } else if (s.equals(first[0])) {
                    Lock.setPin(this, s);
                    d.dismiss();
                    Toast.makeText(this, "PIN lock is on", Toast.LENGTH_SHORT).show();
                } else {
                    first[0] = null;
                    field.setText("");
                    msg.setText("Those did not match. Choose a PIN again.");
                    msg.setTextColor(Ui.RED);
                    ok.setText("Next");
                }
            });
        });
        d.show();
    }

    private void confirmFingerprint() {
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Use fingerprint for Numcha")
                .setSubtitle("Touch the sensor to confirm")
                .setAllowedAuthenticators(Lock.AUTH)
                .setNegativeButton("Cancel", getMainExecutor(), (d, w) -> showLock())
                .build();
        prompt.authenticate(new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult r) {
                        Lock.setFingerprint(SettingsActivity.this, true);
                        showLock();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence s) {
                        showLock();
                    }
                });
    }

    // ---- export / import -----------------------------------------------------

    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "numcha-" + LocalDate.now() + ".zip");
        try {
            startAway(i, REQ_EXPORT);
        } catch (ActivityNotFoundException e) {
            Lock.cameBack();
            Toast.makeText(this, "No file picker on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip",
                        "application/x-zip-compressed", "application/octet-stream"});
        try {
            startAway(i, REQ_IMPORT);
        } catch (ActivityNotFoundException e) {
            Lock.cameBack();
            Toast.makeText(this, "No file picker on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        Lock.cameBack();
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            run("Exporting…", () -> {
                Archive.export(getApplicationContext(), uri);
                return "Exported " + Store.get(getApplicationContext()).count() + " posts.";
            });
        } else if (req == REQ_IMPORT) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Import this file?")
                    .setMessage("Add its posts to your journal, or replace your journal "
                            + "with it? Replacing deletes every post that is not in the file.")
                    .setNeutralButton("Cancel", null)
                    .setNegativeButton("Replace all", (d, w) -> confirmReplace(uri))
                    .setPositiveButton("Add", (d, w) -> doImport(uri, false))
                    .show();
        }
    }

    private void confirmReplace(final Uri uri) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Replace the whole journal?")
                .setMessage("Every post and picture on this phone is deleted and the file's "
                        + "posts take their place.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace", (d, w) -> doImport(uri, true))
                .show();
    }

    private void doImport(final Uri uri, final boolean replace) {
        run("Importing…", () -> {
            Archive.Result r = Archive.importFrom(getApplicationContext(), uri, replace);
            String s = "Imported " + r.added + " new " + (r.added == 1 ? "post" : "posts");
            if (r.updated > 0) s += ", updated " + r.updated;
            if (r.photos > 0) s += ", " + r.photos + " pictures";
            if (r.skipped > 0) s += " (" + r.skipped + " unreadable entries skipped)";
            return s + ".";
        });
    }

    interface Job {
        String run() throws Exception;
    }

    private void run(String label, final Job job) {
        working = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage(label)
                .setCancelable(false)
                .show();
        new Thread(() -> {
            String msg;
            try {
                msg = job.run();
            } catch (Exception e) {
                msg = "That did not work: " + (e.getMessage() == null ? e.toString()
                        : e.getMessage());
            }
            final String m = msg;
            ui.post(() -> {
                if (working != null) working.dismiss();
                working = null;
                if (isDestroyed()) return;
                showData();
                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(m)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }, "archive").start();
    }
}
