package com.numcha;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One journal entry. It always has a time and a colour; everything else is optional. */
final class Post {

    // The colour mark. Stored as these numbers in the database. 0 means "any"
    // in a filter and is never stored.
    static final int ANY = 0;
    static final int GREEN = 1;
    static final int YELLOW = 2;
    static final int RED = 3;
    static final int BLACK = 4;

    static final int[] MOODS = {GREEN, YELLOW, RED, BLACK};
    static final int DEFAULT = YELLOW;

    long id;          // row id, 0 until saved
    String uid;       // stable across export and import, so a re-import updates
    long at;          // when it happened, epoch millis
    String title = "";
    String body = "";
    int mood = DEFAULT;
    String photo;     // file name under files/photos, or null

    static int color(int mood) {
        switch (mood) {
            case GREEN: return Ui.GREEN;
            case YELLOW: return Ui.YELLOW;
            case RED: return Ui.RED;
            default: return Ui.BLACK;
        }
    }

    static String name(int mood) {
        switch (mood) {
            case GREEN: return "Green";
            case YELLOW: return "Yellow";
            case RED: return "Red";
            default: return "Black";
        }
    }

    static int clampMood(int m) {
        return m >= GREEN && m <= BLACK ? m : DEFAULT;
    }

    /** Text that reads on a square or slice of this colour. */
    static int ink(int mood) {
        return mood == GREEN || mood == YELLOW ? Ui.INK_DARK : Ui.TEXT;
    }

    LocalDateTime local() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault());
    }

    LocalDate day() {
        return local().toLocalDate();
    }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    String dateText() {
        return local().format(DATE);
    }

    String timeText() {
        return local().format(TIME);
    }

    static long toMillis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
