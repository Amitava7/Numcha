#!/usr/bin/env bash
# Installs the release APK on a running emulator and uses it the way a person
# would: writes a post with a colour and a changed time, finds it on the
# calendar mosaic, turns the PIN lock on, and checks that a restart asks for
# the PIN and only the right one opens the journal. Fails on any crash.
# Buttons are found by reading the view hierarchy, so this does not depend on
# the screen size. Screenshots land in shots/ and the workflow uploads them.
set -euo pipefail

APK=$(ls apk/*.apk | head -1)
PKG=com.numcha
SHOTS=shots
mkdir -p "$SHOTS"

adb install -r "$APK"
adb root
adb wait-for-device
sleep 5

shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }

# Prints the screen as ASCII in the log, and leaves the percentages of each
# palette colour in $SHOTS/$1.txt for the assertions below to read.
render() {
    adb exec-out screencap > "$SHOTS/$1.raw"
    python3 tools/screen_ascii.py "$SHOTS/$1.raw" "${2:---top}" "${3:-0.0}" \
        "${4:---bottom}" "${5:-1.0}" | tee "$SHOTS/$1.txt"
    rm -f "$SHOTS/$1.raw"
}

pct() {  # pct <name> <COLOUR>  -> percentage, as a float
    awk -v k="PCT_$2" '$1 == k {print $2; f=1} END {if (!f) print 0}' "$SHOTS/$1.txt" | head -1
}

at_least() {  # at_least <value> <minimum>
    awk -v v="$1" -v m="$2" 'BEGIN {exit !(v + 0 >= m + 0)}'
}

# The view hierarchy as XML. uiautomator sometimes cannot get an idle screen
# (a focused text field, the keyboard opening), so try a few times.
ui() {
    local i out
    for i in 1 2 3 4; do
        adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1 || true
        out=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 || true)
        if adb shell cat /sdcard/ui.xml 2>/dev/null | grep -q '<hierarchy'; then
            adb shell cat /sdcard/ui.xml
            return 0
        fi
        echo "uiautomator dump failed ($out), retrying" >&2
        sleep 1
    done
    return 0
}

# GitHub turns ::error:: lines into annotations and drops them from the log,
# so say it twice.
fail() {
    echo "::error::$1"
    echo "SMOKE TEST FAILED: $1"
}

# Taps the middle of the first node matching an attribute, e.g. 'text="Save"'.
tap_node() {
    local match="$1" xy
    xy=$(ui | tr '<' '\n' | grep -F "$match" | head -1 \
        | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 \
        | tr -c '0-9' ' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
    if [ -z "$xy" ]; then
        fail "nothing on screen matches $match"
        ui | tr '<' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' || true
        shot "missing-node"
        exit 1
    fi
    echo "tapping $match at $xy"
    adb shell input tap $xy
    sleep 2
}

expect() {
    if ! ui | grep -qF "$1"; then
        fail "expected to see \"$1\""
        shot "missing-${2:-text}"
        ui | tr '<' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' || true
        exit 1
    fi
}

crashed() {
    if adb logcat -d | grep -q "FATAL EXCEPTION"; then
        fail "$1 crashed"
        adb logcat -d | grep -A 40 "FATAL EXCEPTION"
        shot "$1-crash"
        exit 1
    fi
}

open_screen() {
    adb logcat -c
    adb shell am start -W -n "$PKG/.$1"
    sleep 5
    shot "$1"
    crashed "$1"
}

for SCREEN in MainActivity EditActivity CalendarActivity SettingsActivity; do
    open_screen "$SCREEN"
done

# ---- an empty journal -----------------------------------------------------
adb shell am force-stop "$PKG"
open_screen MainActivity
expect "No posts yet" empty

# ---- write a post ---------------------------------------------------------
tap_node 'text="New post"'
expect "New post" editor
shot Edit-new
# the time can be changed: open both pickers and accept them
tap_node 'text="Change"'
shot Edit-date
tap_node 'text="OK"'
shot Edit-time
tap_node 'text="OK"'
crashed Edit-pickers
tap_node 'content-desc="Green"'
tap_node 'class="android.widget.EditText"'
adb shell input text "Smoke%stest%sday"
sleep 1
adb shell input keyevent 111   # escape: drop the keyboard
sleep 1
shot Edit-filled
tap_node 'text="Save"'
crashed Edit-save
expect "Smoke test day" saved-post
shot Main-one-post

# a post with nothing in it is refused rather than saved
tap_node 'text="New post"'
tap_node 'text="Save"'
expect 'text="Save"' empty-refused
adb shell input keyevent 4
sleep 2
expect "Smoke test day" back-to-list

# ---- calendar ---------------------------------------------------------------
tap_node 'text="Calendar"'
crashed Calendar
expect "Nothing written" calendar-counts
shot Calendar-month
# the month mosaic must really show the green day
# (stopping above the colour key, whose own green square would also count)
render Calendar-month --top 0.15 --bottom 0.50
GREEN=$(pct Calendar-month GREEN)
EMPTY=$(pct Calendar-month EMPTY)
echo "month mosaic: green=$GREEN% empty=$EMPTY%"
if ! at_least "$GREEN" 0.3; then
    fail "the green day is not on the month mosaic"
    exit 1
fi
if ! at_least "$EMPTY" 5; then
    fail "the month mosaic has no empty days drawn"
    exit 1
fi
tap_node 'text="Year"'
crashed Calendar-year
shot Calendar-year
render Calendar-year --top 0.15 --bottom 0.75
if ! at_least "$(pct Calendar-year EMPTY)" 5; then
    fail "the year mosaic did not draw"
    exit 1
fi
tap_node 'text="Month"'
tap_node 'content-desc="Previous"'
tap_node 'content-desc="Next"'
crashed Calendar-nav

# Rotating is the classic way to shake out a crash in a view holding state.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
shot Calendar-landscape
crashed Calendar-rotate
adb shell settings put system user_rotation 0
sleep 3
crashed Calendar-rotate-back
adb shell input keyevent 4
sleep 2

# ---- PIN lock -------------------------------------------------------------
tap_node 'text="Settings"'
expect "Export to a file" settings
tap_node 'text="Lock with PIN"'
expect "Choose a PIN" pin-dialog
# the keyboard's Done key presses the dialog's button
adb shell input text 2468
adb shell input keyevent 66
sleep 2
expect "same PIN again" pin-confirm
adb shell input text 2468
adb shell input keyevent 66
sleep 2
crashed Settings-pin
expect "Change PIN" pin-on
shot Settings-locked

# coming back to a cold start must ask for the PIN
adb shell am force-stop "$PKG"
open_screen MainActivity
expect "Numcha is locked" lock-screen
if ui | grep -qF "Smoke test day"; then
    fail "the journal is visible behind the lock"
    exit 1
fi
for k in 1 1 1 1; do tap_node "text=\"$k\""; done
expect "Wrong PIN" wrong-pin
shot Lock-wrong
for k in 2 4 6 8; do tap_node "text=\"$k\""; done
crashed Lock-unlock
expect "Smoke test day" unlocked
shot Main-unlocked

# turn it off again with the PIN
tap_node 'text="Settings"'
tap_node 'text="Lock with PIN"'
adb shell input text 2468
adb shell input keyevent 66
sleep 2
crashed Settings-unlock
adb shell am force-stop "$PKG"
open_screen MainActivity
expect "Smoke test day" lock-off

echo "every screen opened, a post written and found on the calendar, lock works, no crashes"
