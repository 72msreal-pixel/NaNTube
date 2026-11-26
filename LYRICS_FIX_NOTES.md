# NaNTube Music Lyrics fix

## Changes
- Added `MusicLyrics`, a lyrics resolver inspired by MeeTube's `Lyrics` / `LyricsSources` flow.
- Resolves the recording metadata from YouTube Music first (title, artist, duration, Art Track status and native Lyrics tab).
- Queries YouTube Music lyrics, LRCLIB, BetterLyrics, Unison and KuGou independently.
- Prefers synchronized lyrics when timing is safe; uses plain lyrics without false karaoke highlighting otherwise.
- Validates artist matches and recording duration where available.
- Caches successful results only; misses/errors are retried on the next request.
- The existing NaNTube Compose transcript UI is retained. YouTube captions remain the fallback when external lyrics are unavailable.

## Build verification
The source changes passed `git diff --check`. A Gradle compile could not be run in this environment because this NaNTube archive does not include `gradlew` and no system Gradle executable is installed.
