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

    /**
     * Not a colour a post can have: a day earns it from its posts (see
     * {@link #dayColour}). Shown violet, because blue is already the app's
     * accent colour for buttons and for today's outline.
     */
    static final int EXCEPTIONAL = 5;

    /** The colours a day can show, in the order the chart draws them. */
    static final int[] DAY_COLOURS = {EXCEPTIONAL, GREEN, YELLOW, RED, BLACK};

    /** Green posts a day needs to be exceptional when it has nothing else... */
    static final int GREENS_ALONE = 2;
    /** ...and when it also has a yellow, red or black post: more than four. */
    static final int GREENS_DESPITE = 5;

    /**
     * The colour a day shows. Normally the colour of its latest post; but a
     * day with at least two green posts and no others, or with five or more
     * green posts whatever else it has, is exceptional.
     *
     * @param posts the day's posts, oldest first; not empty
     */
    static int dayColour(java.util.List<Post> posts) {
        int greens = 0, others = 0;
        for (Post p : posts) {
            if (p.mood == GREEN) greens++;
            else others++;
        }
        if ((others == 0 && greens >= GREENS_ALONE) || greens >= GREENS_DESPITE) {
            return EXCEPTIONAL;
        }
        return posts.get(posts.size() - 1).mood;
    }

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
            case EXCEPTIONAL: return Ui.VIOLET;
            default: return Ui.BLACK;
        }
    }

    static String name(int mood) {
        switch (mood) {
            case GREEN: return "Green";
            case YELLOW: return "Yellow";
            case RED: return "Red";
            case EXCEPTIONAL: return "Exceptional";
            default: return "Black";
        }
    }

    static int clampMood(int m) {
        return m >= GREEN && m <= BLACK ? m : DEFAULT;
    }

    /** Text that reads on a square or slice of this colour. */
    static int ink(int mood) {
        return mood == GREEN || mood == YELLOW || mood == EXCEPTIONAL ? Ui.INK_DARK : Ui.TEXT;
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
