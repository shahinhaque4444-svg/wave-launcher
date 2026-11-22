Wave Launcher

A two-screen Android launcher: home shows the clock, date, temperature and a favorites
column; the app list lives on its own screen behind the alphabet wave on the right edge.
Own package `com.bench.wave`, own signature, no dependencies, built on the phone.

Home
- Clock, date, temperature in Celsius, favorites below. Tap to open, long-press for the
  app sheet.
- Swipe up on empty favorites: search. Pull down: notification shade.
- Long-press empty space: settings.

App list
- Open it by tapping the wave or dragging it. Star at the top goes home, back too.
- Tap a letter: that section centres on screen. Double-tap: lock the device.
- Flick to scroll, both directions. Touching a moving list stops it.
- Last app opened in a section moves to the top of that section.
- Long-press a row for favorites, custom icon, app info, hide, uninstall.
- A newly installed app appears on the next visit, no restart.

Themes
- Settings > Appearance > Theme picks a built-in look: Star Trail, Slate, Paper.
- A theme sets the screen colours, the clock face, letter spacing and icon treatment.
- Import theme takes a `.zip`. It unpacks to `files/themes/<name>/` and applies at once.

Theme zip format
```
theme.json
icons/com.example.app.png     optional, one file per package
fonts/clock.ttf               optional, also apps.ttf and ui.ttf
```
```json
{
  "name": "Star Trail",
  "author": "bench",
  "light": false,
  "colors": {
    "background": "#05070C",
    "foreground": "#EDF1F7",
    "mid": "#B4B4BECC",
    "dim": "#7C7C8797",
    "accent": "#AFC4DE"
  },
  "clock": { "style": "thin", "scale": 1.0, "spacing": 0.02 },
  "icons": { "desaturate": false, "tint": "#AFC4DE", "tintStrength": 0.0 }
}
```
Colours take `#RRGGBB` or `#AARRGGBB`. Clock styles: `thin`, `serif`, `mono`, `bold`,
`condensed`. `scale` is 0.7 to 1.4, `spacing` is -0.05 to 0.3. Set
`icons.desaturate` true for a monochrome set, or raise `tintStrength` to wash the icons
toward `tint`. Anything missing falls back to the built-in Star Trail values, so a zip
with only a `name` and `colors` is a valid theme.

Weather
- Temperature sits under the date, Celsius, refreshed every 30 minutes.
- Source: `wttr.in` by IP, falling back to `ipwho.is` for coordinates and Open-Meteo
  for the reading. No key, no location permission, no GPS.
- A failed fetch leaves the last reading on screen.
- Toggle in Settings > Home screen > Weather.

Gestures that need accessibility
- Double-tap a letter locks the screen (`GLOBAL_ACTION_LOCK_SCREEN`).
- Pull down from favorites opens the shade (`GLOBAL_ACTION_NOTIFICATIONS`).
- Enable in Settings > Accessibility. Nothing else is observed.

Settings
- Appearance: theme, import theme, text size, wallpaper dim.
- Home screen: clock, icons, favorite labels, weather.
- App list: wave rail, left-handed rail.
- Library: clear favorites, restore hidden apps.
- Fonts: separate files for clock, app names, interface.
- Gestures & lock: accessibility state.
- About: default launcher, reset icons.

Build and install
```
bash build.sh
su -c "pm install -r -g WaveLauncher-1.2.apk"
```
Needs `apktool`, `aapt2`, `d8`, `apksigner`, `openjdk-21` in Termux and a framework
resource table (`apktool if framework-res.apk`). Signs with `wave.jks` (alias `wave`,
password `wave-launcher`), created on first build.

Source
- `HomeActivity.java` – activity, app loading, search sheet, app menu, uninstall
- `HomeView.java` – both screens on one canvas, all gestures, clock and weather line
- `AppRepo.java` – installed apps, favorites, hidden set, ordering
- `IconLoader.java` – icon cache, custom icons, theme icons and tint
- `Theme.java` – built-in looks, theme.json parsing, zip import
- `Weather.java` – temperature fetch and cache
- `Typefaces.java`, `Prefs.java` – fonts, persisted state
- `WaveLockService.java` – accessibility service for lock and shade
- `SettingsActivity.java` – settings screen
