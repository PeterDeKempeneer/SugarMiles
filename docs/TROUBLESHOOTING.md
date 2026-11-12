# Troubleshooting

## Glucose

**The phone app shows no glucose**

Look at the *Last broadcast* line in the Glucose card:

- **"none received yet"**: xDrip+ isn't reaching SugarMiles.
  - xDrip+: *Broadcast locally* must be on, and *Identify receiver* must contain exactly
    `com.sugarmiles.app`, separated from other apps by a **space**, not a line break.
  - Samsung may have put SugarMiles to sleep: tap *Allow background use* and check
    Settings → Battery → Background usage limits.
  - Wait for the next CGM reading (up to 5 minutes).
- **"Received but couldn't read a value"**: the broadcast arrived in an unexpected format. Please open
  an issue and include the list of extras shown.

**The watch shows `---` or a grey (old) value**

- The phone app must show a recent value first.
- The watch gets glucose over Bluetooth: phone nearby and connected to the watch?
- Open SugarMiles on the watch once; it fetches the last value on start.
- Phone and watch apps built on different computers can't talk to each other. Rebuild both on the
  same machine.

## Workouts

**No heart rate (`♥ --`)**

On the watch: Settings → Apps → SugarMiles → Permissions → allow **Sensors / Heart rate** and
**Physical activity**. Make sure the watch sits snugly on your wrist.

**No map on Strava**

- Allow **Location** for SugarMiles on the watch.
- Start outside and wait until *Searching GPS…* disappears.
- Only walk, run and ride record GPS.

**"Shorter than 1 minute with no data, not saved"**

Workouts under a minute without any heart rate or GPS data are skipped. Anything a minute or
longer is always saved.

**Workout doesn't appear on the phone**

The watch sends the workout when the phone is in Bluetooth range; it's delivered automatically
once they reconnect. Check that the phone app isn't restricted by battery optimisation.

## Strava

**"Add strava.clientId and strava.clientSecret to local.properties and rebuild"**

The keys are missing from `local.properties`, or the app was built before you added them.
Add them (see [setup step 2.2](SETUP.md#22-add-your-strava-keys)), sync and rebuild.

**Strava login fails or doesn't return to the app**

- The Strava API app's *Authorization Callback Domain* must be exactly `localhost`.
- Check the Client ID and Secret in `local.properties`.
- If Strava says uploading wasn't allowed, connect again and keep *Upload activities* ticked.

**Upload failed**

Open the phone app and look at the error under the workout, then tap **Retry**.

- *Network error / will retry*: the phone was offline; it retries automatically.
- *Strava 401*: disconnect and connect Strava again.
- *Strava 429*: rate limit reached; it retries later.
- *duplicate of activity*: it's already on Strava; SugarMiles links to the existing activity.

**The glucose summary isn't in the private note**

The *Glucose summary on Strava* card shows whether Strava accepted the private note. If it shows
*Strava ignored it ✗*, the default setting puts the summary in the description instead.
No summary at all means there were no glucose readings around the workout's time.

## Nightscout

**"Couldn't connect"**: check the URL (including `https://`) and that the site is online.

**"Token wasn't recognized"**: copy the token again from Admin Tools (tap **Show** in the app to
compare). It must be the subject's token, not its name.

**No Exercise entry appears**

- *Add workouts as Exercise* must be switched on.
- The token's role needs permission to add treatments (**careportal** or higher).
- Entries are created when a workout is **saved**, at the workout's start time. Look back on the
  graph to where the workout started.
- Without internet, the entry is sent later automatically.

## Installing

**Wear Installer 2 / ADB can't connect to the watch**

- Watch and phone/computer on the same Wi-Fi (no guest network or hotspot).
- Use the **pairing port** for pairing and the **main Wireless debugging port** for connecting;
  they differ, and the port changes each time wireless debugging is turned on.
- Samsung: Galaxy Wearable app → *Advanced features* → *Remote connection* on.
- Keep the watch close to the router, restart the installer app and try again.

**"App not installed" on the phone or watch**

An older SugarMiles signed with a different key is installed (e.g. built on another computer).
Uninstall it first, then install the new APK.
