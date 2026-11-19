Wave Launcher

A two-screen Android launcher: a home screen with the clock, the date and a column of
favorites, and a separate alphabetical app list reached from the wave on the right edge.
Own package (`com.bench.wave`), own signature, built from source on the phone.

Home
- Clock and date, favorites below (44dp icons, 56dp rows).
- Tap a favorite to open it, long-press for the app menu.
- Swipe up on empty favorites for search.
- Pull down from the favorites for the notification shade (needs the accessibility service).
- Long-press empty space for settings.

App list
- Opened by tapping the wave, or dragging it.
- Sections A–Z inline; the letter under your finger is picked out on the wave.
- Drag to scroll, flick to coast. Touch a moving list and it stops. Both directions alike.
- Tap a letter: that section settles in the middle of the screen.
- The app you used last in a section moves to the top of that section.
- Double-tap a letter to lock the device.
- Long-press a row for favorites, custom icon, app info, hide, uninstall.
- Back or the star at the top returns home.

Settings
- Theme, text size, wallpaper dim.
- Clock, icons, labels, wave alphabet and side.
- Separate fonts for the clock, app names and interface.
- Accessibility state and a direct link to the toggle.

Build
```
bash build.sh
```
Needs `apktool`, `aapt2`, `d8`, `apksigner`, `openjdk-21` in Termux, and a framework
resource table (`apktool if framework-res.apk`). Output `WaveLauncher-1.0.apk`, signed
with `wave.jks` (alias `wave`, password `wave-launcher`, created on first build).

Install
```
su -c "pm install -r -g WaveLauncher-1.0.apk"
```
Then set it as the Home app, and switch it on in Accessibility if you want the
double-tap lock and the shade gesture.

Source
- `HomeActivity.java` – activity, app loading, search, menus, routing
- `HomeView.java` – both screens on one canvas, all gestures
- `AppRepo.java` / `AppEntry.java` – app list, favorites, hidden set, ordering
- `IconLoader.java` – icon cache, custom icons
- `Typefaces.java` / `Prefs.java` – fonts, persisted state
- `WaveLockService.java` – accessibility service for lock and shade
- `SettingsActivity.java` – settings screen
