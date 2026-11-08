# DJPDC — Third-party notices

DJPDC is distributed under the GNU General Public License v3.0 or later (GPL-3.0-or-later).

## Runtime components

| Component | Version | Purpose | License | Source |
|---|---:|---|---|---|
| Jellyfin Media3 FFmpeg decoder | 1.8.0+1 | Additional local audio decoders for Media3 playback | GPL-3.0 | https://github.com/jellyfin/jellyfin-androidx-media |
| AndroidX Media3 | 1.8.0 | Playback engine and media sessions | Apache-2.0 | https://github.com/androidx/media |
| Jetpack Compose / AndroidX | Managed by Gradle | Application UI and Android integration | Apache-2.0 | https://github.com/androidx/androidx |
| Coil | 2.7.0 | Album artwork loading | Apache-2.0 | https://github.com/coil-kt/coil |

## FFmpeg decoder
The artifact `org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1` declares GNU GPL v3.0 in its Maven Central POM and bundles native FFmpeg decoder code. Its source is available at https://github.com/jellyfin/jellyfin-androidx-media.

DJPDC uses the FFmpeg renderer as a fallback. Android platform decoders remain preferred. The app does not copy PixelPlayerOSS source code or branding; PixelPlayerOSS is used as a feature and interface reference only.

See the root `LICENSE` file for the GPL-3.0-or-later license text.
