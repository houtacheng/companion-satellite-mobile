import SwiftUI
import AppIntents
import Network

@main
struct CompanionIOSApp: App {
    @StateObject private var hosts = HostStore()
    var body: some Scene { WindowGroup { ContentView().environmentObject(hosts) } }
}

struct ShortcutControlEntity: AppEntity {
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Companion 控制項")
    static var defaultQuery = ShortcutControlQuery()
    let id: String
    let name: String
    let internetURL: String
    let localURL: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)", subtitle: "Satellite") }
}

struct ShortcutControlQuery: EntityStringQuery {
    private func all() -> [ShortcutControlEntity] {
        let defaults = UserDefaults(suiteName: "group.org.theoakhouse.companion")
        guard let hostData = defaults?.data(forKey: "companion-ios-hosts-v1"),
              let presetData = defaults?.data(forKey: "companion-ios-control-presets-v1"),
              let hosts = try? JSONDecoder().decode([CompanionHost].self, from: hostData),
              let presets = try? JSONDecoder().decode([CompanionControlPreset].self, from: presetData) else { return [] }
        return presets.compactMap { preset in
            guard let host = hosts.first(where: { $0.id == preset.hostID }) else { return nil }
            return ShortcutControlEntity(id: preset.id.uuidString, name: preset.name, internetURL: host.internetURL, localURL: host.localURL)
        }
    }
    func entities(for identifiers: [String]) async throws -> [ShortcutControlEntity] { all().filter { identifiers.contains($0.id) } }
    func suggestedEntities() async throws -> [ShortcutControlEntity] { all() }
    func entities(matching string: String) async throws -> [ShortcutControlEntity] {
        let query = string.trimmingCharacters(in: .whitespacesAndNewlines)
        return query.isEmpty ? all() : all().filter { $0.name.localizedCaseInsensitiveContains(query) }
    }
    func defaultResult() async -> ShortcutControlEntity? { all().first }
}

struct TriggerCompanionShortcutIntent: AppIntent {
    static var title: LocalizedStringResource = "觸發 Companion Satellite 控制項"
    static var description = IntentDescription("直接透過 Satellite 觸發 App 中已建立的 Companion 控制項。")
    static var openAppWhenRun = false
    @Parameter(title: "控制項") var control: ShortcutControlEntity?
    init() { control = nil }
    func perform() async throws -> some IntentResult {
        guard let control else { throw URLError(.badURL) }
        try await ShortcutSatellite.press(internet: control.internetURL, local: control.localURL, serial: "shortcut-\(control.id)")
        return .result()
    }
}

struct OpenCompanionShortcutIntent: AppIntent {
    static var title: LocalizedStringResource = "啟動 Companion"
    static var description = IntentDescription("直接開啟 Companion App。")
    static var openAppWhenRun = true
    func perform() async throws -> some IntentResult { .result() }
}

struct CompanionAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: TriggerCompanionShortcutIntent(), phrases: ["用 \(.applicationName) 觸發控制項","在 \(.applicationName) 執行控制項"], shortTitle: "觸發 Companion", systemImageName: "button.programmable")
        AppShortcut(intent: OpenCompanionShortcutIntent(), phrases: ["啟動 \(.applicationName)","開啟 \(.applicationName)"], shortTitle: "啟動 Companion", systemImageName: "arrow.up.forward.app")
    }
}

private enum ShortcutSatellite {
    static func press(internet: String, local: String, serial: String) async throws {
        if let host = URL(string: local)?.host {
            do { try await pressTCP(host: host, serial: serial); return } catch { }
        }
        try await pressWebSocket(internet: internet, serial: serial)
    }
    private static func safe(_ value: String) -> String { value.lowercased().map { $0.isLetter || $0.isNumber ? $0 : "-" }.reduce("") { $0.last == $1 ? $0 : $0 + String($1) } }
    private static func commands(_ serial: String) -> [String] {
        let id = safe(serial)
        return ["ADD-DEVICE DEVICEID=\(id) PRODUCT_NAME=\"iOS Shortcut\" SERIAL=\"ios:\(id)\" SERIAL_IS_UNIQUE=1 KEYS_TOTAL=1 KEYS_PER_ROW=1 BITMAPS=144 BITMAP_FORMAT=png COLORS=hex TEXT=true TEXT_STYLE=true BRIGHTNESS=0", "KEY-PRESS DEVICEID=\(id) KEY=0 PRESSED=true", "KEY-PRESS DEVICEID=\(id) KEY=0 PRESSED=false", "QUIT"]
    }
    private static func pressWebSocket(internet: String, serial: String) async throws {
        guard let source = URL(string: internet), let host = source.host else { throw URLError(.badURL) }
        var components = URLComponents(); components.scheme = "wss"; components.host = host; components.port = source.port; components.path = "/satellite"
        guard let url = components.url else { throw URLError(.badURL) }
        let task = URLSession.shared.webSocketTask(with: url); task.resume()
        defer { task.cancel(with: .normalClosure, reason: nil) }
        _ = try await task.receive()
        for line in commands(serial) { try await task.send(.string(line + "\n")); if line.contains("ADD-DEVICE") { try await Task.sleep(for: .milliseconds(180)) }; if line.contains("PRESSED=true") { try await Task.sleep(for: .milliseconds(90)) } }
    }
    private static func pressTCP(host: String, serial: String) async throws {
        let connection = NWConnection(host: NWEndpoint.Host(host), port: 16622, using: .tcp)
        defer { connection.cancel() }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.stateUpdateHandler = { state in
                switch state { case .ready: continuation.resume(); case .failed(let error): continuation.resume(throwing: error); default: break }
            }
            connection.start(queue: .global())
        }
        _ = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { data, _, _, error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: data ?? Data()) }
            }
        }
        for line in commands(serial) {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                connection.send(content: Data((line + "\n").utf8), completion: .contentProcessed { error in if let error { continuation.resume(throwing: error) } else { continuation.resume() } })
            }
            if line.contains("ADD-DEVICE") { try await Task.sleep(for: .milliseconds(180)) }; if line.contains("PRESSED=true") { try await Task.sleep(for: .milliseconds(90)) }
        }
    }
}
