package com.numcha;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.os.SystemClock;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The app lock. The PIN is never stored, only a salted PBKDF2 hash of it.
 * Fingerprint is an alternative way past the same lock, so it needs a PIN
 * behind it for when the sensor fails or a finger is wet.
 *
 * "Unlocked" lasts until every screen of the app has gone to the background,
 * except while the user is away in the camera or a file picker we opened.
 */
final class Lock {

    static final int MIN_PIN = 4;
    static final int MAX_PIN = 8;
    private static final int ITERATIONS = 20000;
    private static final int MAX_TRIES = 5;
    private static final long LOCKOUT_MS = 30_000;
    // How long a trip to the camera or a picker may last before we lock anyway.
    private static final long AWAY_GRACE_MS = 10 * 60_000;
    static final int AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private static boolean sUnlocked;
    private static long sAwaySince;

    private Lock() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    static boolean pinOn(Context c) {
        return prefs(c).getString("hash", null) != null;
    }

    static boolean fingerprintOn(Context c) {
        return pinOn(c) && prefs(c).getBoolean("finger", false);
    }

    static int pinLength(Context c) {
        return prefs(c).getInt("len", 0);
    }

    /** Whether the phone has an enrolled fingerprint we are allowed to use. */
    static boolean fingerprintAvailable(Context c) {
        BiometricManager bm = c.getSystemService(BiometricManager.class);
        return bm != null && bm.canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    static boolean needsUnlock(Context c) {
        return pinOn(c) && !sUnlocked;
    }

    static void unlocked() {
        sUnlocked = true;
    }

    /** Called when the last screen of the app stops. */
    static void wentToBackground() {
        if (sAwaySince != 0 && SystemClock.elapsedRealtime() - sAwaySince < AWAY_GRACE_MS) {
            return;
        }
        sUnlocked = false;
    }

    /** Call before opening the camera or a picker, so coming back is not a relock. */
    static void stepAway() {
        sAwaySince = SystemClock.elapsedRealtime();
    }

    static void cameBack() {
        sAwaySince = 0;
    }

    static void setPin(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs(c).edit()
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
                .putInt("len", pin.length())
                .putInt("fails", 0)
                .apply();
        sUnlocked = true;
    }

    static void clearPin(Context c) {
        prefs(c).edit().clear().apply();
        sUnlocked = true;
    }

    static void setFingerprint(Context c, boolean on) {
        prefs(c).edit().putBoolean("finger", on).apply();
    }

    /** Milliseconds until another guess is allowed, 0 if one is allowed now. */
    static long waitMs(Context c) {
        long until = prefs(c).getLong("until", 0);
        long now = System.currentTimeMillis();
        // a clock moved backwards should not lock someone out for days
        if (until - now > LOCKOUT_MS) until = now + LOCKOUT_MS;
        return Math.max(0, until - now);
    }

    static boolean check(Context c, String pin) {
        SharedPreferences p = prefs(c);
        String salt = p.getString("salt", null), stored = p.getString("hash", null);
        if (salt == null || stored == null) return true;
        boolean ok = MessageDigest.isEqual(
                hash(pin, Base64.decode(salt, Base64.NO_WRAP)),
                Base64.decode(stored, Base64.NO_WRAP));
        if (ok) {
            p.edit().putInt("fails", 0).putLong("until", 0).apply();
        } else {
            int fails = p.getInt("fails", 0) + 1;
            SharedPreferences.Editor e = p.edit().putInt("fails", fails);
            if (fails >= MAX_TRIES) {
                e.putInt("fails", 0).putLong("until", System.currentTimeMillis() + LOCKOUT_MS);
            }
            e.apply();
        }
        return ok;
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean validPin(String s) {
        if (s.length() < MIN_PIN || s.length() > MAX_PIN) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }
}
