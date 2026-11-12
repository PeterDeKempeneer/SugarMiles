# SugarMiles

**A Wear OS workout app that shows your live glucose on your wrist while you exercise, and uploads
your workouts to Strava with a glucose summary.**

Built for the Samsung Galaxy Watch Ultra; it should work on most Wear OS 3+ watches.

> **⚠️ Not a medical device.** SugarMiles is a hobby project. Glucose values shown in the app may be
> delayed, missing or wrong. Never use it to make treatment or dosing decisions; always check your
> CGM or meter. Use at your own risk.

## Features

- **Live glucose during workouts:** big number with trend arrow and age, colour-coded
  (green 70–180, orange 181–250, red otherwise, grey when older than 12 minutes).
- **Six sports:** walk, run, ride (with GPS map), swim (time only), workout, weight training.
- **Heart rate, distance, pace/speed**, pause/resume, and an always-on dimmed screen when you lower your wrist.
- **Automatic Strava upload** with the right sport type and map.
- **Glucose summary in Strava's private note** (only you see it), e.g.
  ```
  🩸 Glucose 142 → 118 mg/dL (-24)
  Min 104 · Max 151 · Avg 127
  In range (70-180): 100%
  ```
- **Optional Nightscout entry:** each saved workout appears as an *Exercise* bar on your Nightscout graph.
- **Works offline:** workouts are queued and uploaded once the phone is back online.

## How it works

```
xDrip+ ──broadcast──▶ SugarMiles phone app ──Bluetooth──▶ SugarMiles watch app
                             │  ▲                               │
                             │  └──── finished workout ─────────┘
                             ▼
             Strava (activity + private note)
             Nightscout (Exercise entry, optional)
```

There are two apps: one for the **phone** (receives glucose, talks to Strava and Nightscout) and
one for the **watch** (records the workout). Both are needed.

## What you need

- An Android phone with **xDrip+** receiving your CGM data
- A **Wear OS watch** paired with that phone
- A **Strava** account (a Strava subscription is required to create API apps)
- A computer with **Android Studio** to build the apps
- Optional: a **Nightscout** site

## Getting started

Follow the **[setup guide](docs/SETUP.md)**. It walks through everything in order:

1. [Create your Strava API app](docs/SETUP.md#1-create-your-strava-api-app)
2. [Build the apps](docs/SETUP.md#2-build-the-apps)
3. [Install the phone app](docs/SETUP.md#3-install-the-phone-app)
4. [Install the watch app](docs/SETUP.md#4-install-the-watch-app) (Android Studio, ADB or Wear Installer 2)
5. [Send glucose to SugarMiles](docs/SETUP.md#5-send-glucose-to-sugarmiles) (xDrip+)
6. [Connect Strava](docs/SETUP.md#6-connect-strava)
7. [Nightscout (optional)](docs/SETUP.md#7-nightscout-optional)
8. [Your first workout](docs/SETUP.md#8-your-first-workout)

Something not working? See **[troubleshooting](docs/TROUBLESHOOTING.md)**.

## Using it

1. On the watch, open **SugarMiles**. Your current glucose shows at the top.
2. Tap a sport. The workout starts right away (outdoor sports: wait for *Searching GPS…* to disappear for an accurate map).
3. **II** pauses, **▶** resumes, **■** stops and asks: *Save & upload*, *Resume* or *Discard*.
4. After saving, the phone uploads to Strava and shows a notification with the glucose summary.

Workouts shorter than one minute with no recorded data are not saved.

| Watch sport     | Strava sport type | Map |
|-----------------|-------------------|-----|
| Walk            | Walk              | ✓   |
| Run             | Run               | ✓   |
| Ride            | Ride              | ✓   |
| Swim            | Swim (time only)  | –   |
| Workout         | Workout           | –   |
| Weight training | WeightTraining    | –   |

### Phone app settings

| Setting | What it does |
|---|---|
| **Strava** | Connect or disconnect your Strava account. |
| **Glucose** | Shows the latest reading and the last broadcast received (handy when setting up). |
| **Glucose summary on Strava** | Where the summary goes: private note (default), description, or nowhere. |
| **Nightscout** | URL, access token (Show/Hide to check it) and an on/off switch. |
| **Run in background** | Shown until battery optimisation is turned off for SugarMiles. |
| **Workouts** | Your recent workouts with upload status, *Retry* and *Open in Strava*. |

## Known limitations

- Workout data is kept in memory on the watch until you finish; if the watch app crashes mid-workout, that workout is lost.
- Swimming records time and heart rate only (no laps or distance).
- Live glucose on the watch needs the phone within Bluetooth range.
- Uploads show on Strava as coming from your own API app, not from "Strava" or "Galaxy Watch".
- Glucose is shown in mg/dL.

## Ideas for later

Low-glucose vibration alert, Nightscout fallback over LTE, swim lap counting, strength sets/reps,
glucose graph image, live location sharing.

## For developers

Two Gradle modules in one Android Studio project: `mobile` (phone) and `wear` (watch).
Both use the application ID `com.sugarmiles.app`; the phone and watch can only talk to each other
when both are built and signed on the same machine.

Strava's API base URL is moving to `https://api-v3.strava.com` (available from 4 Jan 2027);
update `StravaClient.API` when needed.

## License

[PolyForm Noncommercial 1.0.0](LICENSE.md): free to use, change and share for personal and other
non-commercial purposes. Commercial use requires permission from the author.

SugarMiles is not affiliated with or endorsed by Strava, Nightscout, xDrip+ or Samsung.
To use it you need your own Strava API application and must follow the
[Strava API Agreement](https://www.strava.com/legal/api).
