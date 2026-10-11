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
latest post, unless it is **exceptional**.

**Exceptional days** are shown in **violet**. No post can be violet; a day
earns it from the sum of its posts:

- two or more green posts and nothing else, or
- more than four (five or more) green posts, even with yellow, red or black
  posts the same day.

Violet rather than blue, because blue is already the app's accent colour (the
buttons, today's outline, the current month's name), so a blue day would read
as "today" or "selected".

Under the squares, a **donut chart** shows how the written days split by
colour (exceptional days are their own slice), with a count on each slice and a legend beside it. Only colours that
occurred appear; days with nothing written are left out of the chart (the line
above it says how many days of the month or year were written).

Tap to list posts underneath:

- **a day**: that day's posts, with *Write for this day*
- **the heading** (*October 2026*, or *2026* in the year view): every post of
  the month or year
- **a month's name** in the year view: every post of that month, with a button
  to open it in the month view
- **a slice of the donut** or a legend row: the posts of that colour in the
  month or year on screen (for *Exceptional*, every post of those days); tap it
  again to close

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

### Updating without losing the journal

Android only installs a new APK over the old one when both are signed with
the **same key**, and uninstalling deletes the journal. So the build signs
with a key kept in four repository secrets (Settings → Secrets and variables
→ Actions → New repository secret):

| secret | value |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore file, base64 encoded (`base64 -w0 numcha.jks`) |
| `KEY_ALIAS` | the key's alias |
| `KEY_PASSWORD` | the key's password |
| `STORE_PASSWORD` | the keystore's password |

The key must not be committed: the repository is public, and anyone holding
the key could sign an "update" that installs over Numcha and reads the
journal. Keep a copy of the key somewhere safe as well, because GitHub will
not show a secret again, and a lost key means one more uninstall.

Each build's version code is the CI run number, so every build is newer than
the last and installs as an update.

Without the secrets, CI still builds, but signs with a throwaway key made for
that run and says so in the job summary; such an APK cannot update an
installed copy.

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
  Post.java              one entry, the colours, and the exceptional-day rule
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
