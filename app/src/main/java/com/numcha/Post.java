package com.numcha;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One journal entry. Everything but the time is optional. */
final class Post {

    // The colour mark. Stored as these numbers, in the database and in exports.
    static final int NONE = 0;
    static final int GREEN = 1;
    static final int YELLOW = 2;
    static final int RED = 3;
    static final int BLACK = 4;

    static final int[] MOODS = {GREEN, YELLOW, RED, BLACK};

    long id;          // row id, 0 until saved
    String uid;       // stable across export and import, so a re-import updates
    long at;          // when it happened, epoch millis
    String title = "";
    String body = "";
    int mood = NONE;
    String photo;     // file name under files/photos, or null

    static int color(int mood) {
        switch (mood) {
            case GREEN: return Ui.GREEN;
            case YELLOW: return Ui.YELLOW;
            case RED: return Ui.RED;
            case BLACK: return Ui.BLACK;
            default: return Ui.PLAIN;
        }
    }

    static String name(int mood) {
        switch (mood) {
            case GREEN: return "Green";
            case YELLOW: return "Yellow";
            case RED: return "Red";
            case BLACK: return "Black";
            default: return "No colour";
        }
    }

    static int clampMood(int m) {
        return m >= GREEN && m <= BLACK ? m : NONE;
    }

    LocalDateTime local() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault());
    }

    LocalDate day() {
        return local().toLocalDate();
    }

    boolean isEmpty() {
        return title.trim().isEmpty() && body.trim().isEmpty() && photo == null
                && mood == NONE;
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
