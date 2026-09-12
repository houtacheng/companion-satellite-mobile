using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

static class BridgePaths
{
    public static readonly string Data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PotPlayerCompanionBridge");
    public static string Settings => Path.Combine(Data, "bridge-settings.json");
    public static string Log => Path.Combine(Data, "bridge.log");
}
static class BridgeLog
{
    static readonly object Gate = new();
    public static void Write(string message)
    {
        lock (Gate)
        {
            try
            {
                Directory.CreateDirectory(BridgePaths.Data);
                if (File.Exists(BridgePaths.Log) && new FileInfo(BridgePaths.Log).Length > 5 * 1024 * 1024)
                    File.Move(BridgePaths.Log, BridgePaths.Log + ".1", true);
                File.AppendAllText(BridgePaths.Log, $"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss zzz} {message}{Environment.NewLine}");
            }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }
    }
}
sealed class BridgeLogProvider : ILoggerProvider
{
    public ILogger CreateLogger(string categoryName) => new FileLogger(categoryName);
    public void Dispose() { }
    sealed class FileLogger(string category) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel level) => level >= LogLevel.Information;
        public void Log<TState>(LogLevel level, EventId id, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            if (IsEnabled(level)) BridgeLog.Write($"[{level}] {category}: {formatter(state, exception)} {exception}");
        }
    }
}

static class TrayProgram
{
    public static bool RestartRequested { get; set; }

    [STAThread]
    static void Main(string[] args)
    {
        using var mutex = new Mutex(true, @"Local\PotPlayerCompanionBridge.Tray", out var first);
        if (!first) return;
        Directory.CreateDirectory(BridgePaths.Data);
        ApplicationConfiguration.Initialize();
        Application.ThreadException += (_, e) => BridgeLog.Write($"Tray error: {e.Exception}");
        AppDomain.CurrentDomain.UnhandledException += (_, e) => BridgeLog.Write($"Fatal error: {e.ExceptionObject}");
        try
        {
            var legacy = Path.Combine(AppContext.BaseDirectory, "bridge-settings.json");
            if (!File.Exists(BridgePaths.Settings) && File.Exists(legacy)) File.Copy(legacy, BridgePaths.Settings);
            var settings = BridgeSettings.LoadOrCreate(BridgePaths.Settings);
            Application.Run(new BridgeTray(args, settings, null));
        }
        catch (Exception error)
        {
            BridgeLog.Write($"Startup error: {error}");
            Application.Run(new BridgeTray(args, null, error));
        }
        if (RestartRequested)
        {
            mutex.ReleaseMutex();
            Process.Start(new ProcessStartInfo(Application.ExecutablePath) { UseShellExecute = true });
        }
    }
}

sealed class BridgeTray : ApplicationContext
{
    readonly NotifyIcon icon;
    readonly ContextMenuStrip menu = new();
    readonly CancellationTokenSource stop = new();
    readonly System.Windows.Forms.Timer timer = new() { Interval = 500 };
    readonly ToolStripMenuItem status = new("啟動中…") { Enabled = false };
    readonly Task server;
    bool exiting;

    public BridgeTray(string[] args, BridgeSettings? settings, Exception? error)
    {
        menu.Items.Add(status);
        menu.Items.Add(new ToolStripSeparator());
        var copy = menu.Items.Add("複製 Token", null, (_, _) =>
        {
            try { Clipboard.SetText(settings!.Token); }
            catch (Exception ex) { BridgeLog.Write($"Clipboard error: {ex.Message}"); icon!.ShowBalloonTip(3000, "複製失敗", "剪貼簿忙碌，請再試一次。", ToolTipIcon.Warning); }
        });
        copy.Enabled = settings != null;
        menu.Items.Add("設定…", null, (_, _) => ShowSettings(settings));
        menu.Items.Add("查看 Log", null, (_, _) => OpenLog());
        menu.Items.Add("開啟設定資料夾", null, (_, _) => Open(BridgePaths.Data));
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("結束", null, (_, _) => Shutdown());
        icon = new NotifyIcon { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application, Text = "PotPlayer Bridge 0.3.0", ContextMenuStrip = menu, Visible = true };
        icon.DoubleClick += (_, _) => ShowSettings(settings);
        BridgeLog.Write("PotPlayer Bridge 0.3.0 tray started");
        server = error != null ? Task.FromException(error) : Task.Run(() => BridgeServer.Run(args, settings!, stop.Token));
        timer.Tick += (_, _) =>
        {
            if (server.IsFaulted)
            {
                status.Text = "啟動失敗／已停止（請查看 Log）";
                icon.Text = "PotPlayer Bridge：錯誤，請查看 Log";
                BridgeLog.Write($"Server failed: {server.Exception!.GetBaseException()}");
                icon.ShowBalloonTip(5000, "PotPlayer Bridge", "服務啟動失敗，請右鍵查看 Log。", ToolTipIcon.Error);
                timer.Stop();
            }
            else if (server.IsCompleted) { status.Text = "服務已停止"; timer.Stop(); }
            else { status.Text = $"Bridge 0.3.0 · Port {settings?.Port}"; }
        };
        timer.Start();
    }

    static void Open(string path)
    {
        try { Process.Start(new ProcessStartInfo(path) { UseShellExecute = true }); }
        catch (Exception ex) { BridgeLog.Write($"Open failed: {ex.Message}"); }
    }
    static void OpenLog()
    {
        try
        {
            var info = new ProcessStartInfo("notepad.exe") { UseShellExecute = true };
            info.ArgumentList.Add(BridgePaths.Log);
            Process.Start(info);
        }
        catch (Exception ex) { BridgeLog.Write($"Open log failed: {ex.Message}"); }
    }
    void ShowSettings(BridgeSettings? settings)
    {
        if (settings == null) return;
        using var dialog = new SettingsForm(settings);
        if (dialog.ShowDialog() != DialogResult.OK) return;
        try
        {
            BridgeSettings.Save(BridgePaths.Settings, dialog.Value);
            BridgeLog.Write("Settings updated; restarting bridge");
            TrayProgram.RestartRequested = true;
            Shutdown();
        }
        catch (Exception ex)
        {
            BridgeLog.Write($"Save settings failed: {ex}");
            MessageBox.Show($"設定無法儲存：{ex.Message}", "PotPlayer Bridge", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
    async void Shutdown()
    {
        if (exiting) return;
        exiting = true;
        timer.Stop();
        status.Text = "正在結束…";
        stop.Cancel();
        try { await server.WaitAsync(TimeSpan.FromSeconds(5)); }
        catch (Exception ex) { BridgeLog.Write($"Shutdown: {ex.Message}"); }
        BridgeLog.Write("Bridge stopped");
        icon.Visible = false;
        ExitThread();
    }
    protected override void Dispose(bool disposing)
    {
        if (disposing) { timer.Dispose(); icon.Dispose(); menu.Dispose(); stop.Dispose(); }
        base.Dispose(disposing);
    }
}

sealed class SettingsForm : Form
{
    readonly NumericUpDown port = new() { Minimum = 1, Maximum = 65535, Width = 110 };
    readonly TextBox token = new() { Width = 360, ReadOnly = true };
    readonly TextBox mediaFolder = new() { Width = 360 };
    readonly TextBox potPlayerPath = new() { Width = 360 };
    public BridgeSettings Value => new((int)port.Value, token.Text.Trim(), mediaFolder.Text.Trim(), potPlayerPath.Text.Trim());

    public SettingsForm(BridgeSettings settings)
    {
        Text = "PotPlayer Companion Bridge 設定";
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(570, 245);
        Font = new Font("Microsoft JhengHei UI", 9F);
        port.Value = settings.Port;
        token.Text = settings.Token;
        mediaFolder.Text = settings.MediaFolder;
        potPlayerPath.Text = settings.PotPlayerPath;

        var table = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(14), ColumnCount = 3, RowCount = 5 };
        table.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 105));
        table.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        table.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 78));
        AddRow(table, 0, "連接埠", port, null);
        AddRow(table, 1, "配對 Token", token, Button("複製", (_, _) => Clipboard.SetText(token.Text)));
        AddRow(table, 2, "媒體資料夾", mediaFolder, Button("瀏覽…", (_, _) => BrowseFolder()));
        AddRow(table, 3, "PotPlayer", potPlayerPath, Button("瀏覽…", (_, _) => BrowsePlayer()));
        var buttons = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.RightToLeft };
        var save = Button("儲存並重啟", (_, _) => SaveAndClose()); save.Width = 110;
        var cancel = Button("取消", (_, _) => Close()); cancel.DialogResult = DialogResult.Cancel;
        buttons.Controls.Add(save); buttons.Controls.Add(cancel);
        table.Controls.Add(buttons, 0, 4); table.SetColumnSpan(buttons, 3);
        Controls.Add(table);
        AcceptButton = save;
        CancelButton = cancel;
    }

    static Button Button(string text, EventHandler click) { var button = new Button { Text = text, AutoSize = true }; button.Click += click; return button; }
    static void AddRow(TableLayoutPanel table, int row, string label, Control field, Control? action)
    {
        table.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        table.Controls.Add(new Label { Text = label, AutoSize = true, Anchor = AnchorStyles.Left }, 0, row);
        field.Anchor = AnchorStyles.Left | AnchorStyles.Right;
        table.Controls.Add(field, 1, row);
        if (action != null) { action.Anchor = AnchorStyles.Left; table.Controls.Add(action, 2, row); }
    }
    void BrowseFolder()
    {
        using var picker = new FolderBrowserDialog { Description = "選擇 Companion 媒體資料夾", UseDescriptionForTitle = true, SelectedPath = Directory.Exists(mediaFolder.Text) ? mediaFolder.Text : "" };
        if (picker.ShowDialog(this) == DialogResult.OK) mediaFolder.Text = picker.SelectedPath;
    }
    void BrowsePlayer()
    {
        using var picker = new OpenFileDialog { Title = "選擇 PotPlayer 執行檔", Filter = "執行檔 (*.exe)|*.exe", FileName = potPlayerPath.Text };
        if (picker.ShowDialog(this) == DialogResult.OK) potPlayerPath.Text = picker.FileName;
    }
    void SaveAndClose()
    {
        if (mediaFolder.Text.Length > 0 && !Directory.Exists(mediaFolder.Text)) { MessageBox.Show("媒體資料夾不存在。", Text, MessageBoxButtons.OK, MessageBoxIcon.Warning); return; }
        if (!File.Exists(potPlayerPath.Text)) { MessageBox.Show("找不到 PotPlayer 執行檔。", Text, MessageBoxButtons.OK, MessageBoxIcon.Warning); return; }
        DialogResult = DialogResult.OK;
        Close();
    }
}
