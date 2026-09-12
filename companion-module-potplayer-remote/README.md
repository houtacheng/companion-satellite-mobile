# companion-module-potplayer-remote

Bitfocus Companion 5.x connection module for PotPlayer Companion Bridge on Windows.

Version 0.2 aligns the everyday Companion workflow with the IINA Remote module: media files become dynamic
presets, files can start from the beginning, and matching actions are provided for target-screen fullscreen,
end-of-playback closing, playlist maintenance, AB loop, rotation, aspect ratio, video adjustments, and window
control. PotPlayer-specific relative volume and speed actions remain available.

Build with `yarn install`, then `yarn package`. Import the generated `.tgz` from Companion → Modules → Import module package. The Windows bridge is in the adjacent `potplayer-companion-bridge` folder.
