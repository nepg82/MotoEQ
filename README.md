# MotoEQ

A native Android equalizer built specifically to fix the failure modes you
described: crash-on-attach, silent non-activation, needing to re-enable
every launch, and volume ducking.

## How it works (no root)

Android has no true "global EQ" API. What actually exists is a session
broadcast: any app that supports external audio effects sends
`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` with its audio session ID when it
starts playing, and a close broadcast when it stops. MotoEQ listens for that
system-wide (via a manifest-registered `BroadcastReceiver`, which Android
exempts from the usual implicit-broadcast restrictions specifically for this
action), and attaches `Equalizer` + `BassBoost` + `LoudnessEnhancer` at max
priority to whatever session just opened — automatically, every time,
without you touching the app. A foreground service keeps the effect chain
alive so it isn't killed while backgrounded during a ride.

This is the same underlying mechanism used by Wavelet, Poweramp's EQ, etc.
The difference here is control over the exact behavior: we set priority to
the max so our effect wins any conflicts, we re-apply your saved gains on
every single session open (this is likely your "have to reset it every
time" bug), and we only build one gain stage (LoudnessEnhancer) instead of
stacking with a separate system loudness feature (likely your ducking bug).

## First build

1. Open this folder (`MotoEQ/`) directly in Android Studio (Koala or newer).
   Let it sync — it will download the Gradle 8.7 distribution and the AGP/
   Kotlin plugins automatically on first sync.
2. Connect your Motorola one 5G via USB with USB debugging enabled
   (Settings → About phone → tap Build number 7x → Developer options →
   USB debugging).
3. Run the app onto the device.

## First run on the phone

- Open MotoEQ once so its foreground service starts (you'll see a "MotoEQ
  active" notification — that's normal and expected, it needs to stay
  running to catch session events).
- Start playing something in YouTube Music. Watch the status line in the
  app: it should change to "Attached • session N • com.google.android.apps.youtube.music"
  within a second or two of playback starting.
- Move the sliders — changes apply live to the current session immediately.

## If YouTube Music doesn't trigger it

Not every app calls the broadcast on every playback start consistently —
this is exactly the flakiness you've been fighting. If MotoEQ shows "Not
attached" while YouTube Music is clearly playing:
1. Fully stop YouTube Music (swipe it from recents) and relaunch it fresh,
   rather than resuming from a paused background state.
2. Check Settings → Sound → Advanced/Audio effects on your phone for any
   competing system equalizer — if one exists and is also grabbing the
   session, disable it so MotoEQ isn't fighting it (this is a plausible
   source of your original ducking issue too).

If it's still unreliable after that, tell me and I'll add a
`NotificationListenerService`-based fallback that watches active
`MediaSession`s directly as a second detection path — it's more invasive
(needs a separate "notification access" grant) but catches apps that are
inconsistent about the broadcast.

## Known limitations to be upfront about

- **Bluetooth / your helmet comms unit**: this approach processes audio in
  the app's mix stage *before* it's handed off for Bluetooth encoding, so it
  should affect what you hear over the helmet. If your phone's Bluetooth
  stack ever hardware-offloads A2DP encoding, effects could bypass it —
  I don't have evidence this Motorola model does that, but it's the one
  variable phone hardware controls that no app (rooted or not) can fully
  guarantee around. Test it on a ride and report back.
- **Session 0 / global mix**: some audio (system sounds, some other apps)
  doesn't go through a per-app session at all, so it won't be affected.
  This is expected and fine for your use case (music app playback).
- No root, so this can't override phone-hardware-level DSP the way a
  Magisk module could. If down the line this still isn't reliable enough,
  that's the fallback option, but let's see how far the non-root version
  gets first.

## What to report back after testing

- Does the "Attached • session N • ..." status appear reliably when you
  start playback?
- Does it survive backgrounding the app / screen off during a ride?
- Any ducking or clipping with LoudnessEnhancer at various levels?
- Exact package name shown when Spotify/other apps are used, if you want
  those tuned too.
