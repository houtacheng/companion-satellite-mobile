using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net.WebSockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;

static class BridgeServer
{
public static async Task Run(string[] args, BridgeSettings settings, CancellationToken cancellationToken)
{
var settingsPath = BridgePaths.Settings;

var builder = WebApplication.CreateBuilder(new WebApplicationOptions { Args = args, ContentRootPath = BridgePaths.Data });
builder.Logging.ClearProviders(); builder.Logging.AddProvider(new BridgeLogProvider());
builder.WebHost.ConfigureKestrel(options => options.ListenAnyIP(settings.Port));
var app = builder.Build();
app.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });

var controller = new PotPlayerController(settings);
var clients = new ConcurrentDictionary<Guid, WebSocket>();
var jsonOptions = new JsonSerializerOptions(JsonSerializerDefaults.Web);

app.MapGet("/", () => Results.Json(new { name = "PotPlayer Companion Bridge", version = "0.3.0", status = "running", websocket = "/ws" }));
app.Map("/ws", async (HttpContext context) =>
{
    if (!context.WebSockets.IsWebSocketRequest) { context.Response.StatusCode = 400; return; }
    using var socket = await context.WebSockets.AcceptWebSocketAsync();
    var id = Guid.NewGuid();
    var authenticated = false;
    var buffer = new byte[64 * 1024];
    try
    {
        while (socket.State == WebSocketState.Open)
        {
            var result = await socket.ReceiveAsync(buffer, context.RequestAborted);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await socket.CloseOutputAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Client closed connection",
                    CancellationToken.None);
                break;
            }
            using var document = JsonDocument.Parse(buffer.AsMemory(0, result.Count));
            var root = document.RootElement;
            var type = root.TryGetProperty("type", out var typeNode) ? typeNode.GetString() : null;
            var requestId = root.TryGetProperty("requestId", out var requestNode) ? requestNode.GetString() : null;

            if (!authenticated)
            {
                var token = root.TryGetProperty("token", out var tokenNode) ? tokenNode.GetString() : null;
                authenticated = type == "auth" && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(token ?? ""), Encoding.UTF8.GetBytes(settings.Token));
                await Send(socket, new { type = "auth_result", ok = authenticated, requestId, state = authenticated ? controller.ReadState() : null }, jsonOptions, context.RequestAborted);
                if (!authenticated) await socket.CloseAsync(WebSocketCloseStatus.PolicyViolation, "Authentication failed", context.RequestAborted);
                else { clients[id] = socket; BridgeLog.Write("Client authenticated"); }
                continue;
            }

            if (type == "get_state")
            {
                await Send(socket, new { type = "state", state = controller.ReadState(), requestId }, jsonOptions, context.RequestAborted);
            }
            else if (type == "refresh_library")
            {
                await Send(socket, new { type = "library", folder = settings.MediaFolder, files = controller.ScanLibrary(), requestId }, jsonOptions, context.RequestAborted);
            }
            else if (type == "command")
            {
                var command = root.TryGetProperty("command", out var commandNode) ? commandNode.GetString() ?? "" : "";
                var argsNode = root.TryGetProperty("args", out var suppliedArgs) ? suppliedArgs : default;
                try
                {
                    controller.Execute(command, argsNode); BridgeLog.Write($"Command completed: {command}");
                    await Send(socket, new { type = "command_result", ok = true, requestId, state = controller.ReadState() }, jsonOptions, context.RequestAborted);
                }
                catch (Exception error)
                {
                    await Send(socket, new { type = "command_result", ok = false, requestId, error = error.Message }, jsonOptions, context.RequestAborted);
                }
            }
        }
    }
    finally { clients.TryRemove(id, out _); BridgeLog.Write("Client disconnected"); }
});

using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(500));
_ = Task.Run(async () =>
{
    while (await timer.WaitForNextTickAsync(cancellationToken))
    {
        var payload = JsonSerializer.SerializeToUtf8Bytes(new { type = "state", state = controller.ReadState() }, jsonOptions);
        foreach (var pair in clients)
        {
            try { if (pair.Value.State == WebSocketState.Open) await pair.Value.SendAsync(payload, WebSocketMessageType.Text, true, CancellationToken.None); }
            catch { clients.TryRemove(pair.Key, out _); }
        }
    }
});

BridgeLog.Write($"PotPlayer Companion Bridge: ws://0.0.0.0:{settings.Port}");
BridgeLog.Write($"Settings: {settingsPath}");
await app.RunAsync(cancellationToken);

}

static async Task Send(WebSocket socket, object value, JsonSerializerOptions options, CancellationToken cancellationToken)
{
    var bytes = JsonSerializer.SerializeToUtf8Bytes(value, options);
    await socket.SendAsync(bytes, WebSocketMessageType.Text, true, cancellationToken);
}

}

sealed record BridgeSettings(int Port, string Token, string MediaFolder, string PotPlayerPath)
{
    static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web) { WriteIndented = true };

    public static BridgeSettings LoadOrCreate(string path)
    {
        if (File.Exists(path)) return JsonSerializer.Deserialize<BridgeSettings>(File.ReadAllText(path), JsonOptions) ?? throw new InvalidDataException("bridge-settings.json 格式不正確");
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        var created = new BridgeSettings(19191, Convert.ToHexString(RandomNumberGenerator.GetBytes(24)).ToLowerInvariant(), "", Path.Combine(programFiles, "DAUM", "PotPlayer", "PotPlayerMini64.exe"));
        Save(path, created);
        BridgeLog.Write("已建立 bridge-settings.json，請保存其中的配對金鑰。");
        return created;
    }

    public static void Save(string path, BridgeSettings settings)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(settings, JsonOptions), new UTF8Encoding(false));
        File.Move(temporary, path, true);
    }
}

sealed class PotPlayerController
{
    const uint WmCommand = 0x0111, WmUser = 0x0400, WmClose = 0x0010;
    const int GetVolume = 0x5000, SetVolume = 0x5001, GetTotalTime = 0x5002, GetCurrentTime = 0x5004, SetCurrentTime = 0x5005;
    const int GetPlayStatus = 0x5006, SetPlayStatus = 0x5007, SetPlayOrder = 0x5008, GetMute = 0x5011, SetMute = 0x5012, GetSpeed = 0x5015, SetSpeed = 0x5016;
    readonly BridgeSettings settings;
    string currentUrl = "";
    bool fullscreen, onTop, subtitleVisible = true, autoCloseOnEnd, closeScheduled;
    string loopFile = "no", loopPlaylist = "no";
    int abLoopStep;
    int previousStatus;
    bool playbackFinished;

    public PotPlayerController(BridgeSettings settings) => this.settings = settings;

    public object ReadState()
    {
        var window = FindWindow();
        if (window == IntPtr.Zero) return EmptyState();
        var status = Query(window, GetPlayStatus);
        var durationMs = Math.Max(0, Query(window, GetTotalTime));
        var positionMs = Math.Clamp(Query(window, GetCurrentTime), 0, Math.Max(durationMs, 0));
        if (previousStatus == 2 && status == 0 && durationMs > 0 && positionMs >= durationMs - 1500) playbackFinished = true;
        if (status == 2) { playbackFinished = false; closeScheduled = false; }
        if (playbackFinished && autoCloseOnEnd && !closeScheduled)
        {
            closeScheduled = true;
            _ = Task.Run(async () => { await Task.Delay(1000); if (IsWindow(window)) PostMessage(window, WmClose, IntPtr.Zero, IntPtr.Zero); });
        }
        previousStatus = status;
        var duration = durationMs / 1000d;
        var position = positionMs / 1000d;
        var remaining = Math.Max(0, duration - position);
        var title = GetTitle(window).Replace(" - PotPlayer", "", StringComparison.OrdinalIgnoreCase);
        return new {
            playback = status == 2 ? "playing" : status == 1 ? "paused" : "idle", paused = status != 2, idle = status == 0,
            position, duration, remaining, remainingSeconds = (int)Math.Ceiling(remaining), playbackFinished,
            progress = duration > 0 ? position / duration * 100 : 0, speed = Query(window, GetSpeed) / 1000d,
            volume = Query(window, GetVolume), muted = Query(window, GetMute) == 1, title, url = currentUrl,
            fullscreen, pip = false, ontop = onTop, playlistPosition = -1, playlistCount = 0,
            chapter = -1, chapterCount = 0, audioTrack = (int?)null, videoTrack = (int?)null, subtitleTrack = (int?)null,
            secondSubtitleTrack = (int?)null, audioDelay = 0, subtitleDelay = 0, subtitleVisible,
            loopFile, loopPlaylist, filename = !string.IsNullOrWhiteSpace(currentUrl) ? Path.GetFileName(currentUrl) : title,
            videoInfo = VideoInfo(window), endBehavior = autoCloseOnEnd ? "close" : loopFile == "inf" ? "loop" : "hold",
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
    }

    object EmptyState() => new { playback = "idle", paused = true, idle = true, position = 0, duration = 0, remaining = 0, remainingSeconds = 0, playbackFinished = false, progress = 0, speed = 1, volume = 0, muted = false, title = "PotPlayer 未執行", url = "", fullscreen = false, pip = false, ontop = false, playlistPosition = -1, playlistCount = 0, chapter = -1, chapterCount = 0, audioTrack = (int?)null, videoTrack = (int?)null, subtitleTrack = (int?)null, secondSubtitleTrack = (int?)null, audioDelay = 0, subtitleDelay = 0, subtitleVisible = true, loopFile, loopPlaylist, filename = "", videoInfo = "", endBehavior = autoCloseOnEnd ? "close" : loopFile == "inf" ? "loop" : "hold", timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() };

    public void Execute(string command, JsonElement args)
    {
        if (command == "open") { Open(GetString(args, "url"), GetBool(args, "startFromBeginning")); return; }
        if (command == "playlist_add") { AddToPlaylist(GetString(args, "url")); return; }
        if (command == "set_auto_close_on_end") { autoCloseOnEnd = GetBool(args, "enabled"); return; }
        var window = FindWindow();
        if (window == IntPtr.Zero) throw new InvalidOperationException("找不到 PotPlayer 視窗，請先啟動 PotPlayer");
        switch (command)
        {
            case "play": Send(window, WmUser, SetPlayStatus, 2); break;
            case "pause": Send(window, WmUser, SetPlayStatus, 1); break;
            case "toggle_play_pause": Send(window, WmUser, SetPlayStatus, 0); break;
            case "stop": Send(window, WmCommand, 20002, 0); break;
            case "seek_relative": Send(window, WmUser, SetCurrentTime, Math.Max(0, Query(window, GetCurrentTime) + (int)(GetNumber(args, "seconds") * 1000))); break;
            case "seek_absolute": Send(window, WmUser, SetCurrentTime, Math.Max(0, (int)(GetNumber(args, "seconds") * 1000))); break;
            case "set_position_percent": Send(window, WmUser, SetCurrentTime, (int)(Math.Max(0, Query(window, GetTotalTime)) * Math.Clamp(GetNumber(args, "percent"), 0, 100) / 100)); break;
            case "set_volume": Send(window, WmUser, SetVolume, (int)Math.Clamp(GetNumber(args, "volume"), 0, 100)); break;
            case "change_volume": Send(window, WmUser, SetVolume, Math.Clamp(Query(window, GetVolume) + (int)GetNumber(args, "amount"), 0, 100)); break;
            case "set_mute": Send(window, WmUser, SetMute, GetBool(args, "muted") ? 1 : 0); break;
            case "toggle_mute": Send(window, WmUser, SetMute, Query(window, GetMute) == 1 ? 0 : 1); break;
            case "set_speed": Send(window, WmUser, SetSpeed, (int)(Math.Clamp(GetNumber(args, "speed"), 0.2, 12) * 1000)); break;
            case "change_speed": Send(window, WmUser, SetSpeed, (int)(Math.Clamp(Query(window, GetSpeed) / 1000d + GetNumber(args, "amount"), 0.2, 12) * 1000)); break;
            case "reset_speed": Send(window, WmUser, SetSpeed, 1000); break;
            case "playlist_previous": Send(window, WmUser, SetPlayOrder, 0); break;
            case "playlist_next": Send(window, WmUser, SetPlayOrder, 1); break;
            case "toggle_fullscreen": Command(window, 10013); fullscreen = !fullscreen; break;
            case "set_fullscreen": if (fullscreen != GetBool(args, "enabled")) { Command(window, 10013); fullscreen = !fullscreen; } break;
            case "fullscreen_on_screen": FullscreenOnScreen(window, (int)GetNumber(args, "screen")); break;
            case "set_ontop": if (onTop != GetBool(args, "enabled")) { Command(window, 10337); onTop = !onTop; } break;
            case "toggle_ontop": Command(window, 10337); onTop = !onTop; break;
            case "frame_step": Command(window, 10241); break;
            case "frame_back_step": Command(window, 10242); break;
            case "screenshot": Command(window, 10224); break;
            case "close_window": Send(window, WmClose, 0, 0); break;
            case "playlist_clear": Command(window, 10212); break;
            case "playlist_shuffle": Command(window, 10069); break;
            case "ab_loop": AbLoop(window, GetString(args, "operation")); break;
            case "set_subtitle_visibility": if (subtitleVisible != GetBool(args, "enabled")) { Command(window, 10126); subtitleVisible = !subtitleVisible; } break;
            case "toggle_loop_file": Command(window, 10497); loopFile = loopFile == "inf" ? "no" : "inf"; break;
            case "toggle_loop_playlist": Command(window, 10496); loopPlaylist = loopPlaylist == "inf" ? "no" : "inf"; break;
            case "set_rotation": SetRotation(window, (int)GetNumber(args, "degrees")); break;
            case "set_aspect": SetAspect(window, GetString(args, "aspect")); break;
            case "adjust_video": AdjustVideo(window, GetString(args, "property"), (int)GetNumber(args, "amount")); break;
            case "window_control": WindowControl(window, GetString(args, "operation")); break;
            default: throw new NotSupportedException($"不支援的命令：{command}");
        }
    }

    public object[] ScanLibrary()
    {
        if (string.IsNullOrWhiteSpace(settings.MediaFolder) || !Directory.Exists(settings.MediaFolder)) return [];
        var extensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { ".mp4", ".mkv", ".mov", ".avi", ".wmv", ".webm", ".m4v", ".mp3", ".wav", ".flac", ".m2ts", ".ts" };
        return Directory.EnumerateFiles(settings.MediaFolder, "*", SearchOption.AllDirectories).Where(f => extensions.Contains(Path.GetExtension(f))).Take(2000).Select(f => (object)new { label = Path.GetRelativePath(settings.MediaFolder, f), path = f }).ToArray();
    }

    void Open(string path, bool startFromBeginning)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("媒體路徑不可為空");
        var executable = File.Exists(settings.PotPlayerPath) ? settings.PotPlayerPath : "PotPlayerMini64.exe";
        Process.Start(new ProcessStartInfo(executable, $"\"{path}\"") { UseShellExecute = true });
        currentUrl = path; playbackFinished = false;
        if (startFromBeginning)
            _ = Task.Run(async () => { await Task.Delay(700); var window = FindWindow(); if (window != IntPtr.Zero) Command(window, 10243); });
    }

    void AddToPlaylist(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("加入播放清單的路徑不可為空");
        var executable = File.Exists(settings.PotPlayerPath) ? settings.PotPlayerPath : "PotPlayerMini64.exe";
        Process.Start(new ProcessStartInfo(executable, $"\"{path}\" /add") { UseShellExecute = true });
    }

    void FullscreenOnScreen(IntPtr window, int screenNumber)
    {
        var screens = Screen.AllScreens;
        if (screenNumber < 1 || screenNumber > screens.Length) throw new ArgumentOutOfRangeException(nameof(screenNumber), $"找不到螢幕 {screenNumber}");
        if (fullscreen) { Command(window, 10013); fullscreen = false; }
        var bounds = screens[screenNumber - 1].WorkingArea;
        ShowWindow(window, 9);
        SetWindowPos(window, IntPtr.Zero, bounds.X, bounds.Y, bounds.Width, bounds.Height, 0x0004 | 0x0010);
        Command(window, 10013);
        fullscreen = true;
    }

    void AbLoop(IntPtr window, string operation)
    {
        if (operation == "cancel") { Command(window, 10253); abLoopStep = 0; return; }
        if (abLoopStep == 0) { Command(window, 10249); abLoopStep = 1; }
        else if (abLoopStep == 1) { Command(window, 10250); abLoopStep = 2; }
        else { Command(window, 10253); abLoopStep = 0; }
    }

    static void SetRotation(IntPtr window, int degrees) => Command(window, degrees switch { 90 => 10614, 180 => 10615, 270 => 10616, _ => 10613 });

    static void SetAspect(IntPtr window, string aspect) => Command(window, aspect switch
    {
        "4:3" => 10017, "16:9" => 10018, "16:10" => 10019, "1.85:1" => 10020, "2.35:1" => 10021, _ => 10016
    });

    static void AdjustVideo(IntPtr window, string property, int amount)
    {
        var commands = property switch
        {
            "brightness" => (down: 10086, up: 10087), "contrast" => (down: 10088, up: 10089),
            "saturation" => (down: 10090, up: 10091), "hue" => (down: 10092, up: 10093),
            _ => throw new ArgumentException($"不支援的影像調整項目：{property}")
        };
        for (var i = 0; i < Math.Min(Math.Abs(amount), 100); i++) Command(window, amount < 0 ? commands.down : commands.up);
    }

    static void WindowControl(IntPtr window, string operation)
    {
        if (operation == "minimize") ShowWindow(window, 6);
        else if (operation == "restore") ShowWindow(window, 9);
        else if (operation == "toggle_playlist") Command(window, 10011);
        else throw new ArgumentException($"不支援的視窗操作：{operation}");
    }

    static string VideoInfo(IntPtr window)
    {
        var width = Query(window, 0x6030); var height = Query(window, 0x6031); var fps = Query(window, 0x6032);
        return width > 0 && height > 0 ? $"{width}×{height}{(fps > 0 ? $" · {fps / 1000d:0.###} fps" : "")}" : "";
    }

    static IntPtr FindWindow()
    {
        IntPtr found = IntPtr.Zero;
        EnumWindows((window, _) => { var title = GetTitle(window); if (IsWindowVisible(window) && title.Contains("PotPlayer", StringComparison.OrdinalIgnoreCase)) { found = window; return false; } return true; }, IntPtr.Zero);
        return found;
    }
    static string GetTitle(IntPtr window) { var length = GetWindowTextLength(window); var text = new StringBuilder(length + 1); GetWindowText(window, text, text.Capacity); return text.ToString(); }
    static int Query(IntPtr window, int command) => unchecked((int)SendMessage(window, WmUser, (IntPtr)command, IntPtr.Zero));
    static void Command(IntPtr window, int command) => Send(window, WmCommand, command, 0);
    static void Send(IntPtr window, uint message, int command, int value) => SendMessage(window, message, (IntPtr)command, (IntPtr)value);
    static string GetString(JsonElement args, string name) => args.ValueKind == JsonValueKind.Object && args.TryGetProperty(name, out var node) ? node.GetString() ?? "" : "";
    static double GetNumber(JsonElement args, string name) => args.ValueKind == JsonValueKind.Object && args.TryGetProperty(name, out var node) && node.TryGetDouble(out var value) ? value : 0;
    static bool GetBool(JsonElement args, string name) => args.ValueKind == JsonValueKind.Object && args.TryGetProperty(name, out var node) && node.ValueKind == JsonValueKind.True;

    delegate bool EnumWindowsProc(IntPtr window, IntPtr parameter);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr window, StringBuilder text, int maxCount);
    [DllImport("user32.dll")] static extern int GetWindowTextLength(IntPtr window);
    [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr window, int command);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr window, IntPtr insertAfter, int x, int y, int width, int height, uint flags);
}
