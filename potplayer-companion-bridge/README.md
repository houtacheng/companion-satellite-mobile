# PotPlayer Companion Bridge

Windows bridge between Bitfocus Companion and PotPlayer. It uses PotPlayer's `WM_USER` control interface and exposes the same authenticated WebSocket envelope as the existing IINA module.

## Windows development run

1. Install the .NET 8 SDK.
2. Run `dotnet run` in this folder once. The bridge creates `bridge-settings.json` with a random pairing token and listens on port `19191` (`/ws`).
3. Set `mediaFolder` and `potPlayerPath` if their defaults do not match the PC.
4. Allow TCP port `19191` through Windows Firewall only for trusted private networks.
5. In Companion, add **PotPlayer Remote**, enter the Windows PC address, port `19191`, and the generated token.

## Publish a standalone Windows build

```powershell
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

Version 0.3 supports transport, exact seek, volume, mute, speed, previous/next, target-screen fullscreen,
always-on-top, frame step, screenshot, subtitle visibility, repeat toggles, AB loop, playlist add/clear/shuffle,
rotation, common aspect ratios, relative video adjustments, minimize/restore, media opening, media-folder scan,
close-on-finish, and live state polling.

Use only on a trusted LAN or VPN. WebSocket traffic is authenticated but not encrypted.
