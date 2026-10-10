# Numcha

A daily journal that lives only on your phone. Write a post about your day,
give the day a colour, add a picture if you like, and watch the year fill in
as a mosaic.

Built the same way as [Terraquiz](https://github.com/Amitava7/Terraquiz): a
plain Android app in Java with **no libraries at all** (no AndroidX, no
Kotlin runtime), screens built in code, and the APK produced by GitHub Actions.
There is nothing to install locally.

## What it does

**Posts.** "New post" stamps the current date and time. Tap *Change* to move
it to another date or time, e.g. to write up yesterday. Every post has a
**colour** (green, yellow, red or black), which starts as yellow; everything
else is optional:

- a **title**
- some **text**
- a **picture**, taken with the camera or picked from the gallery

Tap a post in the list to edit or delete it.

**Filtering the journal.** Above the list:

- the **date** chip (*All dates* ▾) narrows the list to *a day*, *a month* or
  *a year*; the ‹ › arrows beside it step to the previous or next one
- the **colour** chips (*All*, *Green*, *Yellow*, *Red*, *Black*) show one
  colour only; tap the chosen chip again to show every colour
- the two combine (e.g. red posts in March), and *Clear* resets both

**Calendar.** *Month* shows a calendar of coloured squares, one per day.
*Year* shows all twelve months, 365 squares. A day takes the colour of its
latest post.

Under the squares, a **donut chart** shows how the written days split by
colour, with a count on each slice and a legend beside it. Only colours that
occurred appear; days with nothing written are left out of the chart (the line
above it says how many days of the month or year were written).

Tap to list posts underneath:

- **a day**: that day's posts, with *Write for this day*
- **the heading** (*October 2026*, or *2026* in the year view): every post of
  the month or year
- **a month's name** in the year view: every post of that month, with a button
  to open it in the month view
- **a slice of the donut** or a legend row: the posts of that colour in the
  month or year on screen; tap it again to close

**Lock.** In Settings, *Lock with PIN* (4 to 8 digits) and *Unlock with
fingerprint* can each be switched on and off. With the lock on, Numcha asks for
it every time it is opened or comes back from the background. Fingerprint
needs the PIN on as well, as the way in when the sensor will not read. The PIN
is stored only as a salted PBKDF2 hash, and five wrong guesses lock the pad for
30 seconds. Going to the camera or the photo picker from inside the app does
not count as leaving it.

**Export and import.** *Export to a file* writes one `.zip` wherever you
choose (Downloads, a USB stick, a cloud drive app). *Import from a file* reads
one back, on this phone or a new one, and either **adds** its posts to the
journal or **replaces** the whole journal with it. A post that is already in
the journal (from an earlier export) is updated, not duplicated, so importing
the same file twice is harmless.

The zip is readable on a computer too:

```
journal.json   {"app":"numcha","version":1,"posts":[
                 {"uid":"…","at":1759955160000,"date":"2026-10-08T21:46",
                  "title":"…","text":"…","colour":"green","photo":"photos/….jpg"}]}
photos/….jpg
```

## Privacy

- Posts are in a SQLite database and pictures are JPEG files, both in the
  app's private storage. Other apps cannot read them.
- The app has **no internet permission**, so it cannot send anything anywhere.
  The only permission it asks for is fingerprint.
- Android cloud backup and device-to-device transfer are both switched off for
  it, so the journal is not copied to Google's servers. Use Export to move it.
- With the lock on, the app is blanked in the recent-apps view.
- Pictures are shrunk to at most 2048 px on the long side when added, so a
  year of photos stays a manageable export.

If you forget the PIN, the only way back in is clearing the app's data, which
deletes the journal. Export every so often.

## Getting the APK

Every push builds one. Open the **Actions** tab, pick the latest *Build APK*
run and download the `numcha-apk` artifact. Pushing a tag like `v1.0` also
publishes a GitHub release with the APK attached.

By default CI signs with a throwaway key generated for that run, which means
consecutive builds have different signatures, so you have to uninstall the
old copy before installing a new one. **Uninstalling deletes the journal**, so
export first, or set up a stable signature once by adding four repository
secrets: `KEYSTORE_BASE64` (`base64 -w0 your.jks`), `KEY_ALIAS`,
`KEY_PASSWORD` and `STORE_PASSWORD`. Then every build installs over the last
one and keeps your posts.

Target device is a Galaxy S24 Ultra, so `minSdk` is 34 and there is no
compatibility code for anything older.

## What CI does

1. **Build release APK**: assembles, signs, prints a size breakdown in the
   job summary and fails past a 1.5 MB budget.
2. **Launch on an emulator**: installs the APK on Android 14 and uses it by
   finding buttons in the view hierarchy. It opens every screen, saves a
   colour-only post (yellow by default) and a green one with a changed date
   and time, filters the list by colour and by day, finds the green day on the
   month mosaic, lists posts from the donut legend and the month heading, opens
   the year view and lists the year, rotates the screen, turns the PIN lock on,
   restarts the app and checks that the journal is hidden, that a wrong PIN is
   rejected and the right one opens it, and turns the lock off again. It prints the calendar as ASCII in the log
   (`tools/screen_ascii.py`) and fails on any crash. Screenshots are uploaded
   as the `screenshots` artifact.

The fingerprint prompt, the camera and the file pickers are system screens and
are not driven by the smoke test.

## Layout

```
app/src/main/java/com/numcha/
  Post.java              one entry, and the colour constants
  Store.java             SQLite + the photos folder
  Photos.java            shrinking, saving and loading pictures
  Archive.java           export / import as a zip
  Lock.java              PIN hash, attempt limit, when to relock
  App.java  Base.java    relocking when the app leaves the screen
  MainActivity.java      the list of posts and its filters
  EditActivity.java      writing and editing a post
  CalendarActivity.java  month / year mosaic, donut, post lists
  MosaicView.java        draws the squares
  PieView.java           the donut of days by colour
  PostCard.java          one post as a card, used by both lists
  SettingsActivity.java  lock and data settings
  LockActivity.java      PIN pad and fingerprint prompt
  CameraFiles.java       a one-file provider the camera app writes into
tools/
  smoke_test.sh          drives the APK on an emulator
  screen_ascii.py        turns a screenshot into ASCII for the CI log
```

## Building locally

Needs JDK 17 and an Android SDK with platform 35:

```bash
./gradlew assembleRelease     # add -PncStoreFile=... to sign it
```
