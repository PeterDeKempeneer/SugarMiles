# SugarMiles setup guide

This guide takes you from nothing to your first workout on Strava. Plan about an hour the first time.

**Contents**

1. [Create your Strava API app](#1-create-your-strava-api-app)
2. [Build the apps](#2-build-the-apps)
3. [Install the phone app](#3-install-the-phone-app)
4. [Install the watch app](#4-install-the-watch-app)
5. [Send glucose to SugarMiles](#5-send-glucose-to-sugarmiles)
6. [Connect Strava](#6-connect-strava)
7. [Nightscout (optional)](#7-nightscout-optional)
8. [Your first workout](#8-your-first-workout)
9. [Updating SugarMiles](#9-updating-sugarmiles)

---

## 1. Create your Strava API app

SugarMiles uploads to Strava through your own, personal Strava API app. Creating one requires a
Strava subscription.

1. Go to <https://www.strava.com/settings/api> and log in.
2. Fill in the form:

   | Field | Value |
   |---|---|
   | Application Name | `SugarMiles` (shown on your uploaded activities) |
   | Category | anything, e.g. *Training* |
   | Club | leave empty |
   | Website | `http://localhost` |
   | Application Description | optional |
   | **Authorization Callback Domain** | **`localhost`** (exactly this, no `http://` or slashes) |

3. Upload an app icon (Strava requires one before it shows your keys).
4. Note the **Client ID** and **Client Secret** (click *show*). Keep the secret private.

## 2. Build the apps

### 2.1 Get the code and open it

1. Install [Android Studio](https://developer.android.com/studio) (any recent version).
2. Download or clone this repository.
3. Android Studio → **File → Open** → select the `SugarMiles` folder (the one containing `settings.gradle.kts`) → **Trust project**.
4. Wait for the Gradle sync to finish. The first sync downloads a lot and can take several minutes.

> **Tip:** avoid keeping the project in a OneDrive/Dropbox folder. File syncing can lock build files
> and cause random build errors.

### 2.2 Add your Strava keys

Open `local.properties` in the project root (Android Studio creates it during the first sync;
it already contains an `sdk.dir=` line). Add:

```properties
strava.clientId=12345
strava.clientSecret=your_client_secret

# Optional: prefills the Nightscout URL in the phone app
nightscout.url=https://your-site.example.com
```

`local.properties` is excluded from git, so your keys never end up in the repository.
Sync again after editing (**File → Sync Project with Gradle Files**).

### 2.3 Build the APKs

**In Android Studio:** **Build → Generate App Bundles or APKs → Generate APKs**
(older versions: **Build → Build Bundle(s) / APK(s) → Build APK(s)**).

**Or from a terminal** in the project folder:

```powershell
.\gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug            # macOS / Linux
```

You get two files:

| App | File |
|---|---|
| Phone | `mobile/build/outputs/apk/debug/mobile-debug.apk` |
| Watch | `wear/build/outputs/apk/debug/wear-debug.apk` |

> **Important:** always build the phone and watch app **on the same computer**. They're signed with
> that computer's debug key, and the phone and watch only talk to each other when the keys match.

> **Don't share your built APKs publicly.** Your Strava Client Secret is built into the phone app.
> Others should build SugarMiles with their own Strava API app.

## 3. Install the phone app

Pick one:

- **USB from Android Studio:** enable USB debugging on the phone (Settings → About phone →
  Software information → tap *Build number* 7×, then Developer options → *USB debugging*).
  Connect the phone, select **mobile** and your phone at the top of Android Studio, press **▶**.
- **APK file:** copy `mobile-debug.apk` to the phone (USB, Google Drive, …), tap it and allow
  installing from that source when asked.
- **ADB:** `adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk`

Then open SugarMiles on the phone:

1. Allow notifications.
2. Tap **Allow background use** in the *Run in background* card. Without this, Samsung phones may
   stop SugarMiles in the background, which breaks glucose forwarding and uploads.
   On Samsung, also check Settings → Battery → Background usage limits and make sure SugarMiles
   is not in *Sleeping apps* (adding it to *Never sleeping apps* is safest).

## 4. Install the watch app

The watch can't install an APK by tapping it; you install it over **wireless debugging**.
The phone app must already be installed (step 3).

### 4.1 Prepare the watch (all methods)

1. Watch and phone/computer on the **same Wi-Fi** (not a guest network or phone hotspot).
2. On the watch: **Settings → About watch → Software information** → tap **Software version**
   several times until developer mode is turned on.
3. **Settings → Developer options** → turn on **ADB debugging** and **Wireless debugging**.
4. Open **Wireless debugging**: note the **IP address and port** shown there.

Samsung: if it won't connect, open the **Galaxy Wearable** app on the phone and check that
*Advanced features → Remote connection* is on, and keep the watch close to the router.

### 4.2 Install, choose one method

**Option A: Wear Installer 2 (no computer needed for installing)**

1. Install **Wear Installer 2** (by Malcolm Bryant) from the Play Store on your phone.
2. Copy `wear-debug.apk` to the phone's **Downloads** folder.
3. In Wear Installer 2, enter the watch's **IP address** (without the port).
4. **Pair:** on the watch, in Wireless debugging, tap **Pair new device**. It shows a **pairing code**
   and a **pairing port**. In Wear Installer 2, enter the code, a space, and the pairing port.
   It should report that pairing succeeded. If not, close the app and try again.
5. Close and reopen Wear Installer 2. Enter the IP and the **port from the main Wireless debugging
   screen** (this is a different port from the pairing port).
6. Select `wear-debug.apk` and install it.

The developer of Wear Installer 2 has a video walkthrough linked from the Play Store page.

**Option B: Android Studio**

1. In Android Studio: **Device Manager → Pair devices using Wi-Fi** (or use the ADB commands below).
2. Select **wear** and your watch at the top, press **▶**.

**Option C: ADB from the computer**

ADB is in the Android SDK: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` on Windows.

```powershell
adb pair <watch-ip>:<pairing-port>      # enter the pairing code shown on the watch
adb connect <watch-ip>:<port>           # port from the main Wireless debugging screen
adb devices                              # the watch should be listed as "device"
adb -s <watch-ip>:<port> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

### 4.3 First launch on the watch

1. Open **SugarMiles** on the watch.
2. Allow all permissions: **sensors / heart rate**, **physical activity**, **location** (while using
   the app) and **notifications**. Without sensor permission there is no heart rate; without
   location there is no map.
3. Turn **ADB debugging** off again on the watch to save battery.

## 5. Send glucose to SugarMiles

SugarMiles listens to the glucose broadcast from **xDrip+**. Glucose is always handled in mg/dL.

### xDrip+

**Settings → Inter-app settings:**

| Setting | Value |
|---|---|
| **Broadcast locally** | On |
| **Identify receiver** | `com.sugarmiles.app` |

If *Identify receiver* already contains another app (e.g. `com.example.otherapp`),
add SugarMiles after it, **separated by a space**:

```
com.example.otherapp com.sugarmiles.app
```

> A line break instead of a space silently stops delivery.

### Check it

Within 5 minutes (at the next CGM reading), the phone app's **Glucose** card shows your value, and
*Last broadcast* shows when it arrived. The watch shows it at the top of the sport list.

## 6. Connect Strava

1. In the phone app, tap **Connect Strava**.
2. Approve in Strava. Keep **"Upload activities"** ticked.
3. You return to SugarMiles, which shows *Connected as …*.

**Glucose summary on Strava** lets you choose where the summary goes:

| Option | Result |
|---|---|
| Private note (fallback: description) | Default. Only you see it. |
| Private note only | Only you see it; nothing added if Strava ever stops accepting private notes. |
| Description | Visible to everyone who can see the activity. |
| Don't add to Strava | Summary is only kept in the app. |

## 7. Nightscout (optional)

When enabled, every saved workout is added to Nightscout as one **Exercise** entry at the workout's
real start time, with its duration and a note such as:

```
🏃 Run · 42:10 · 7.20 km · avg HR 148 · glucose 142 → 118 mg/dL
```

It's an informational marker only: it doesn't set a temp target and doesn't affect AAPS dosing.
Discarded workouts are never sent.

### 7.1 Create an access token

SugarMiles only needs permission to **add** treatments, so use the built-in **careportal** role
instead of your API secret.

1. Open your Nightscout site and log in with your API secret.
2. Menu (☰, top right) → **Admin Tools**.
3. Under *Subjects*, click **Add new Subject**.
4. Name: `sugarmiles`, Roles: **careportal**. Save.
5. The subject's **access token** appears in the list (it looks like `sugarmiles-1a2b3c4d5e6f7a8b`).
   Copy it.

More about tokens and roles: [Nightscout Admin Tools documentation](https://nightscout.github.io/nightscout/admin_tools).

### 7.2 Enter it in the app

In the phone app's **Nightscout** card:

1. Check the **Nightscout URL** (e.g. `https://your-site.example.com`).
2. Paste the **access token**. Tap **Show** to check you entered it correctly.
3. Tap **Save & test**. It should say *Connected ✓ (token recognized)*.
4. Turn on **Add workouts as Exercise**.

## 8. Your first workout

Do a short test (just over a minute):

1. On the watch, open SugarMiles and pick **Walk** (outside, for GPS).
2. Check that glucose, the timer and **♥** heart rate appear (heart rate takes 10–20 seconds).
3. Tap **■** → **Save & upload**.
4. On the phone: a notification says the workout was uploaded. In the app, the workout shows
   *On Strava ✓* and *Glucose added to: private note*.
5. In Strava, open the activity: the map, heart rate and the private note with your glucose summary
   should be there. If Nightscout is on, the Exercise bar appears on your Nightscout graph.

## 9. Updating SugarMiles

1. Pull or download the new code and open it in Android Studio.
2. Build both APKs again (step 2.3) **on the same computer** as before.
3. Install them over the existing apps (steps 3 and 4). Your settings, Strava connection and
   workout history are kept.

If the application ID ever changes (or you build on a different computer), Android sees a new app:
uninstall the old one first, then reconnect Strava and update xDrip+'s *Identify receiver*.
