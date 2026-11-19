Wave Launcher

A two-screen Android launcher: home shows the clock, date and a favorites column; the
app list lives on its own screen behind the alphabet wave on the right edge. Own
package `com.bench.wave`, own signature, no dependencies, built on the phone.

Home
- Clock and date, favorites below. Tap to open, long-press for the app sheet.
- Swipe up on empty favorites: search. Pull down: notification shade.
- Long-press empty space: settings.

App list
- Open it by tapping the wave or dragging it. Star at the top goes home, back too.
- Tap a letter: that section centres on screen. Double-tap: lock the device.
- Flick to scroll, both directions. Touching a moving list stops it.
- Last app opened in a section moves to the top of that section.
- Long-press a row for favorites, custom icon, app info, hide, uninstall.

Gestures that need accessibility
- Double-tap a letter locks the screen (`GLOBAL_ACTION_LOCK_SCREEN`).
- Pull down from favorites opens the shade (`GLOBAL_ACTION_NOTIFICATIONS`).
- Enable in Settings > Accessibility. Nothing else is observed.

Settings
- Appearance: theme, text size, wallpaper dim.
- Home screen: clock, icons, favorite labels.
- App list: wave rail, left-handed rail.
- Library: clear favorites, restore hidden apps.
- Fonts: separate files for clock, app names, interface.
- Gestures & lock: accessibility state.
- About: default launcher, reset icons.

Build and install
```
bash build.sh
su -c "pm install -r -g WaveLauncher-1.1.apk"
```
Needs `apktool`, `aapt2`, `d8`, `apksigner`, `openjdk-21` in Termux and a framework
resource table (`apktool if framework-res.apk`). Signs with `wave.jks` (alias `wave`,
password `wave-launcher`), created on first build.

Source
- `HomeActivity.java` – activity, app loading, search sheet, app menu, uninstall
- `HomeView.java` – both screens on one canvas, all gestures
- `AppRepo.java` – installed apps, favorites, hidden set, ordering
- `IconLoader.java` – icon cache and custom icons
- `Typefaces.java`, `Prefs.java` – fonts, persisted state
- `WaveLockService.java` – accessibility service for lock and shade
- `SettingsActivity.java` – settings screen
