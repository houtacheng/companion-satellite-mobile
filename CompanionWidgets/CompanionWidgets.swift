import WidgetKit
import SwiftUI
import AppIntents
import Network

private struct StoredHost:Codable { let id:UUID;let name:String;let internetURL:String;let localURL:String }
private struct StoredControlPreset:Codable {let id:UUID;let name:String;let icon:String;let hostID:UUID}
struct KeyVisual { var text="";var background="000000";var foreground="ffffff";var image:Data? }
struct CompanionHostEntity:AppEntity {
    static var typeDisplayRepresentation=TypeDisplayRepresentation(name:"Companion 主機")
    static var defaultQuery=CompanionHostQuery()
    let id:String;let name:String;let internetURL:String;let localURL:String
    var displayRepresentation:DisplayRepresentation { DisplayRepresentation(title:"\(name)",subtitle:"\(internetURL)") }
}
struct CompanionHostQuery:EntityQuery {
    private func all()->[CompanionHostEntity] {
        guard let defaults=UserDefaults(suiteName:"group.org.theoakhouse.companion"),let data=defaults.data(forKey:"companion-ios-hosts-v1"),let records=try? JSONDecoder().decode([StoredHost].self,from:data) else{return []}
        return records.map{CompanionHostEntity(id:$0.id.uuidString,name:$0.name,internetURL:$0.internetURL,localURL:$0.localURL)}
    }
    func entities(for identifiers:[String]) async throws->[CompanionHostEntity] { all().filter{identifiers.contains($0.id)} }
    func suggestedEntities() async throws->[CompanionHostEntity] { all() }
}
struct CompanionControlEntity:AppEntity {
    static var typeDisplayRepresentation=TypeDisplayRepresentation(name:"Companion Satellite 控制項")
    static var defaultQuery=CompanionControlQuery()
    let id:String;let name:String;let icon:String;let internetURL:String;let localURL:String
    var displayRepresentation:DisplayRepresentation{DisplayRepresentation(title:"\(name)",subtitle:"Satellite",image:.init(systemName:icon))}
}
struct CompanionControlQuery:EntityStringQuery {
    init() {}
    private func all()->[CompanionControlEntity] {
        let defaults=UserDefaults(suiteName:"group.org.theoakhouse.companion")
        defaults?.synchronize()
        guard let hostData=defaults?.data(forKey:"companion-ios-hosts-v1"),
              let presetData=defaults?.data(forKey:"companion-ios-control-presets-v1"),
              let hosts=try? JSONDecoder().decode([StoredHost].self,from:hostData),
              let presets=try? JSONDecoder().decode([StoredControlPreset].self,from:presetData) else{return []}
        return presets.compactMap { preset in
            guard let host=hosts.first(where:{$0.id==preset.hostID}) else{return nil}
            return CompanionControlEntity(id:preset.id.uuidString,name:preset.name,icon:preset.icon,internetURL:host.internetURL,localURL:host.localURL)
        }
    }
    func entities(for identifiers:[String])async throws->[CompanionControlEntity]{all().filter{identifiers.contains($0.id)}}
    func suggestedEntities()async throws->[CompanionControlEntity]{all()}
    func entities(matching string:String)async throws->[CompanionControlEntity]{
        let needle=string.trimmingCharacters(in:.whitespacesAndNewlines)
        return needle.isEmpty ? all():all().filter{$0.name.localizedCaseInsensitiveContains(needle)}
    }
    func defaultResult()async->CompanionControlEntity?{all().first}
}

private enum CompanionSatellite {
    static func snapshot(internet:String,serial:String,rows:Int,columns:Int) async->[KeyVisual] {
        var output=Array(repeating:KeyVisual(),count:rows*columns)
        guard let source=URL(string:internet),let host=source.host else{return output}
        // The saved public Companion URL may use an http:// spelling, while
        // Cloudflare Tunnel exposes Satellite WebSockets over TLS.  Following
        // an http -> https redirect does not reliably upgrade a WebSocket, so
        // public Satellite connections must start as wss://.
        var c=URLComponents();c.scheme="wss";c.host=host;c.port=source.port;c.path="/satellite"
        guard let url=c.url else{return output}
        let config=URLSessionConfiguration.ephemeral;config.timeoutIntervalForRequest=3
        let task=URLSession(configuration:config).webSocketTask(with:url);task.resume();defer{task.cancel(with:.normalClosure,reason:nil)}
        do {
            _ = try await task.receive();try await task.send(.string(add(serial,rows,columns)+"\n"))
            var found=Set<Int>()
            while found.count<rows*columns {
                let message=try await task.receive();let value:String
                switch message {case .string(let s):value=s;case .data(let d):value=String(data:d,encoding:.utf8) ?? "";@unknown default:value=""}
                for line in value.split(whereSeparator:{$0=="\n"||$0=="\r"}).map(String.init) where line.hasPrefix("KEY-STATE") {
                    let p=params(line);guard let raw=p["KEY"] ?? p["CONTROLID"] else{continue}
                    let pieces=raw.split(separator:"/");let index:Int?
                    if pieces.count>=2,let row=Int(pieces[0]),let column=Int(pieces[1]){index=row*columns+column}else{index=Int(raw)}
                    guard let index,output.indices.contains(index) else{continue}
                    if let encoded=p["TEXT"],let data=Data(base64Encoded:encoded){output[index].text=String(data:data,encoding:.utf8) ?? ""}
                    output[index].background=p["COLOR"] ?? "000000";output[index].foreground=p["TEXTCOLOR"] ?? "ffffff"
                    if let raw=p["BITMAP"] {let encoded=raw.split(separator:",").last.map(String.init) ?? raw;output[index].image=Data(base64Encoded:encoded)}
                    found.insert(index)
                }
            }
            try? await task.send(.string("QUIT\n"))
        } catch { }
        return output
    }
    private static func params(_ line:String)->[String:String] {var out:[String:String]=[:];let pattern=#"([A-Za-z_]+)=(?:\"([^\"]*)\"|(\S+))"#;guard let regex=try? NSRegularExpression(pattern:pattern)else{return out};let ns=line as NSString;for match in regex.matches(in:line,range:NSRange(location:0,length:ns.length)){let key=ns.substring(with:match.range(at:1)).uppercased();let r=match.range(at:2).location != NSNotFound ? match.range(at:2):match.range(at:3);out[key]=ns.substring(with:r)};return out}
    static func press(internet:String,local:String,serial:String,rows:Int,columns:Int,index:Int) async throws {
        if let host=URL(string:local)?.host {
            do { try await pressTCP(host:host,serial:serial,rows:rows,columns:columns,index:index);return } catch { }
        }
        try await pressWebSocket(internet:internet,serial:serial,rows:rows,columns:columns,index:index)
    }
    static func rotate(internet:String,serial:String,rows:Int,columns:Int,index:Int,direction:Int) async throws {
        guard let source=URL(string:internet),let host=source.host else { throw URLError(.badURL) }
        var c=URLComponents();c.scheme="wss";c.host=host;c.port=source.port;c.path="/satellite"
        guard let url=c.url else { throw URLError(.badURL) }
        let task=URLSession.shared.webSocketTask(with:url);task.resume()
        defer { task.cancel(with:.normalClosure,reason:nil) }
        _ = try await task.receive()
        let id=safe(serial)
        try await task.send(.string(add(serial,rows,columns)+"\n"))
        try await Task.sleep(for:.milliseconds(180))
        try await task.send(.string("KEY-ROTATE DEVICEID=\(id) KEY=\(index) DIRECTION=\(direction < 0 ? -1:1)\n"))
    }
    private static func safe(_ value:String)->String { value.lowercased().map{ $0.isLetter||$0.isNumber ? $0:"-" }.reduce(""){ $0.last==$1 ? $0:$0+String($1) } }
    private static func add(_ serial:String,_ rows:Int,_ columns:Int)->String {
        let id=safe(serial)
        return "ADD-DEVICE DEVICEID=\(id) PRODUCT_NAME=\"iOS Companion \(columns)x\(rows)\" SERIAL=\"ios:\(id)\" SERIAL_IS_UNIQUE=1 KEYS_TOTAL=\(rows*columns) KEYS_PER_ROW=\(columns) BITMAPS=144 BITMAP_FORMAT=png COLORS=hex TEXT=true TEXT_STYLE=true BRIGHTNESS=0"
    }
    private static func commands(_ serial:String,_ rows:Int,_ columns:Int,_ index:Int)->[String] {
        let id=safe(serial);return [add(serial,rows,columns),"KEY-PRESS DEVICEID=\(id) KEY=\(index) PRESSED=true","KEY-PRESS DEVICEID=\(id) KEY=\(index) PRESSED=false","QUIT"]
    }
    private static func pressWebSocket(internet:String,serial:String,rows:Int,columns:Int,index:Int) async throws {
        guard let source=URL(string:internet),let host=source.host else { throw URLError(.badURL) }
        var c=URLComponents();c.scheme="wss";c.host=host;c.port=source.port;c.path="/satellite"
        guard let url=c.url else { throw URLError(.badURL) }
        let task=URLSession.shared.webSocketTask(with:url);task.resume()
        defer { task.cancel(with:.normalClosure,reason:nil) }
        _ = try await task.receive()
        for line in commands(serial,rows,columns,index) { try await task.send(.string(line+"\n"));if line.contains("ADD-DEVICE"){try await Task.sleep(for:.milliseconds(180))};if line.contains("PRESSED=true"){try await Task.sleep(for:.milliseconds(90))};if line.contains("PRESSED=false"){try await Task.sleep(for:.milliseconds(180))} }
    }
    private static func pressTCP(host:String,serial:String,rows:Int,columns:Int,index:Int) async throws {
        let connection=NWConnection(host:NWEndpoint.Host(host),port:16622,using:.tcp)
        defer { connection.cancel() }
        try await withCheckedThrowingContinuation { (continuation:CheckedContinuation<Void,Error>) in
            var completed=false
            connection.stateUpdateHandler={state in
                guard !completed else{return}
                if case .ready=state {completed=true;continuation.resume()}
                else if case .failed(let error)=state {completed=true;continuation.resume(throwing:error)}
            }
            connection.start(queue:.global())
        }
        _ = try await withCheckedThrowingContinuation { (continuation:CheckedContinuation<Data,Error>) in
            connection.receive(minimumIncompleteLength:1,maximumLength:65536){data,_,_,error in
                if let error {continuation.resume(throwing:error)} else {continuation.resume(returning:data ?? Data())}
            }
        }
        for line in commands(serial,rows,columns,index) {
            try await withCheckedThrowingContinuation { (continuation:CheckedContinuation<Void,Error>) in
                connection.send(content:Data((line+"\n").utf8),completion:.contentProcessed{error in if let error{continuation.resume(throwing:error)}else{continuation.resume()}})
            }
            if line.contains("ADD-DEVICE"){try await Task.sleep(for:.milliseconds(180))};if line.contains("PRESSED=true"){try await Task.sleep(for:.milliseconds(90))}
            if line.contains("PRESSED=false"){try await Task.sleep(for:.milliseconds(180))}
        }
    }
}
private actor SatelliteSnapshotCoordinator {func load(internet:String,serial:String,rows:Int,columns:Int)async->[KeyVisual]{await CompanionSatellite.snapshot(internet:internet,serial:serial,rows:rows,columns:columns)}}
private let satelliteSnapshots=SatelliteSnapshotCoordinator()

struct CompanionPressIntent: AppIntent {
    static var title: LocalizedStringResource = "觸發 Companion 按鈕"
    static var openAppWhenRun = false
    @Parameter(title: "網際網路網址") var internetURL: String
    @Parameter(title: "區網網址") var localURL: String
    @Parameter(title:"Surface ID") var serial:String
    @Parameter(title:"列數") var rows:Int
    @Parameter(title:"欄數") var columns:Int
    @Parameter(title:"按鈕") var index:Int
    init(){internetURL="";localURL="";serial="";rows=1;columns=1;index=0}
    init(internet:String,local:String,serial:String,rows:Int,columns:Int,index:Int){self.internetURL=internet;self.localURL=local;self.serial=serial;self.rows=rows;self.columns=columns;self.index=index}
    func perform() async throws -> some IntentResult {
        try await CompanionSatellite.press(internet:internetURL,local:localURL,serial:serial,rows:rows,columns:columns,index:index)
        reloadEveryCompanionWidget()
        try? await Task.sleep(for:.milliseconds(250))
        reloadEveryCompanionWidget()
        return .result()
    }
}

private func reloadEveryCompanionWidget(){for kind in ["Companion1x1","Companion3x2","Companion4x4","CompanionRotary1x1","CompanionMixed4x4"]{WidgetCenter.shared.reloadTimelines(ofKind:kind)};WidgetCenter.shared.reloadAllTimelines()}

struct CompanionRotateIntent:AppIntent {
    static var title:LocalizedStringResource="旋轉 Companion 旋鈕"
    static var openAppWhenRun=false
    @Parameter(title:"網際網路網址") var internetURL:String
    @Parameter(title:"Surface ID") var serial:String
    @Parameter(title:"列數") var rows:Int
    @Parameter(title:"欄數") var columns:Int
    @Parameter(title:"按鈕") var index:Int
    @Parameter(title:"方向") var direction:Int
    init(){internetURL="";serial="";rows=1;columns=1;index=0;direction=1}
    init(internet:String,serial:String,rows:Int,columns:Int,index:Int,direction:Int){internetURL=internet;self.serial=serial;self.rows=rows;self.columns=columns;self.index=index;self.direction=direction}
    func perform() async throws->some IntentResult {try await CompanionSatellite.rotate(internet:internetURL,serial:serial,rows:rows,columns:columns,index:index,direction:direction);reloadEveryCompanionWidget();try? await Task.sleep(for:.milliseconds(250));reloadEveryCompanionWidget();return .result()}
}

struct CompanionControlIntent: AppIntent, ControlConfigurationIntent {
    static var title: LocalizedStringResource = "設定 Companion 控制項"
    static var description = IntentDescription("設定要直接觸發的 Companion 主機與按鈕位置。")
    static var openAppWhenRun = false
    @Parameter(title:"App 控制項") var control:CompanionControlEntity?
    init(){control=nil}
    func perform() async throws -> some IntentResult {
        guard let control else{throw URLError(.badURL)}
        try await CompanionSatellite.press(internet:control.internetURL,local:control.localURL,serial:"control-\(control.id)",rows:1,columns:1,index:0)
        return .result()
    }
}

struct CompanionWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "設定 Companion 小工具"
    static var description = IntentDescription("指定主機與 Satellite Surface 編號。")
    @Parameter(title:"名稱", default:"Companion") var name:String
    @Parameter(title:"主機") var host:CompanionHostEntity?
    @Parameter(title:"Surface 編號", default:1) var page:Int
    @Parameter(title:"允許按下與旋轉", default:true) var interactive:Bool
    init(){name="Companion";host=nil;page=1;interactive=true}
}

struct CompanionEntry: TimelineEntry { let date:Date;let configuration:CompanionWidgetIntent;let visuals:[KeyVisual] }

private func loadEntry(_ configuration:CompanionWidgetIntent,rows:Int,columns:Int,tag:String="grid") async->CompanionEntry {guard let host=configuration.host else{return CompanionEntry(date:.now,configuration:configuration,visuals:[])};let serial="widget-\(host.id)-\(tag)-\(columns)x\(rows)-\(configuration.page)";let visuals=await satelliteSnapshots.load(internet:host.internetURL,serial:serial,rows:rows,columns:columns);return CompanionEntry(date:.now,configuration:configuration,visuals:visuals)}
struct Provider1x1:AppIntentTimelineProvider {func placeholder(in context:Context)->CompanionEntry{CompanionEntry(date:.now,configuration:CompanionWidgetIntent(),visuals:[])};func snapshot(for c:CompanionWidgetIntent,in context:Context)async->CompanionEntry{await loadEntry(c,rows:1,columns:1)};func timeline(for c:CompanionWidgetIntent,in context:Context)async->Timeline<CompanionEntry>{Timeline(entries:[await loadEntry(c,rows:1,columns:1)],policy:.after(.now.addingTimeInterval(300)))}}
struct Provider3x2:AppIntentTimelineProvider {func placeholder(in context:Context)->CompanionEntry{CompanionEntry(date:.now,configuration:CompanionWidgetIntent(),visuals:[])};func snapshot(for c:CompanionWidgetIntent,in context:Context)async->CompanionEntry{await loadEntry(c,rows:2,columns:3)};func timeline(for c:CompanionWidgetIntent,in context:Context)async->Timeline<CompanionEntry>{Timeline(entries:[await loadEntry(c,rows:2,columns:3)],policy:.after(.now.addingTimeInterval(300)))}}
struct Provider4x4:AppIntentTimelineProvider {func placeholder(in context:Context)->CompanionEntry{CompanionEntry(date:.now,configuration:CompanionWidgetIntent(),visuals:[])};func snapshot(for c:CompanionWidgetIntent,in context:Context)async->CompanionEntry{await loadEntry(c,rows:4,columns:4)};func timeline(for c:CompanionWidgetIntent,in context:Context)async->Timeline<CompanionEntry>{Timeline(entries:[await loadEntry(c,rows:4,columns:4)],policy:.after(.now.addingTimeInterval(300)))}}
struct ProviderRotary1x1:AppIntentTimelineProvider {func placeholder(in context:Context)->CompanionEntry{CompanionEntry(date:.now,configuration:CompanionWidgetIntent(),visuals:[])};func snapshot(for c:CompanionWidgetIntent,in context:Context)async->CompanionEntry{await loadEntry(c,rows:1,columns:1,tag:"rotary")};func timeline(for c:CompanionWidgetIntent,in context:Context)async->Timeline<CompanionEntry>{Timeline(entries:[await loadEntry(c,rows:1,columns:1,tag:"rotary")],policy:.after(.now.addingTimeInterval(300)))}}
struct ProviderMixed4x4:AppIntentTimelineProvider {func placeholder(in context:Context)->CompanionEntry{CompanionEntry(date:.now,configuration:CompanionWidgetIntent(),visuals:[])};func snapshot(for c:CompanionWidgetIntent,in context:Context)async->CompanionEntry{await loadEntry(c,rows:4,columns:4,tag:"mixed")};func timeline(for c:CompanionWidgetIntent,in context:Context)async->Timeline<CompanionEntry>{Timeline(entries:[await loadEntry(c,rows:4,columns:4,tag:"mixed")],policy:.after(.now.addingTimeInterval(300)))}}

private struct CompanionGrid:View {
    let entry:CompanionEntry;let rows:Int;let columns:Int;var rotaryRows:Set<Int>=[];var surfaceTag="grid"
    private var safeInset:CGFloat { rows >= 4 ? 10:4 }
    var body:some View {
        VStack(spacing:5) {
            ForEach(0..<rows,id:\.self){r in
                HStack(spacing:5){ForEach(0..<columns,id:\.self){c in
                    let index=r*columns+c
                    let serial="widget-\(entry.configuration.host?.id ?? "none")-\(surfaceTag)-\(columns)x\(rows)-\(entry.configuration.page)"
                    let visual=entry.visuals.indices.contains(index) ? entry.visuals[index]:KeyVisual(text:"\(index+1)")
                    if rotaryRows.contains(r) { RotaryCell(visual:visual,internet:entry.configuration.host?.internetURL ?? "",serial:serial,rows:rows,columns:columns,index:index,interactive:entry.configuration.interactive) }
                    else if entry.configuration.interactive { Button(intent:CompanionPressIntent(internet:entry.configuration.host?.internetURL ?? "",local:entry.configuration.host?.localURL ?? "",serial:serial,rows:rows,columns:columns,index:index)){KeyFace(visual:visual,round:true)}.buttonStyle(.plain) }
                    else { KeyFace(visual:visual,round:true) }
                }}
            }
        }.padding(safeInset).containerBackground(Color.clear,for:.widget)
    }
}

private struct KeyFace:View {let visual:KeyVisual;let round:Bool;var body:some View{ZStack{(round ? AnyShape(RoundedRectangle(cornerRadius:9,style:.continuous)):AnyShape(Circle())).fill(Color(hex:visual.background));if let data=visual.image,let image=UIImage(data:data){Image(uiImage:image).resizable().scaledToFill().clipShape(round ? AnyShape(RoundedRectangle(cornerRadius:9,style:.continuous)):AnyShape(Circle()))};(round ? AnyShape(RoundedRectangle(cornerRadius:9,style:.continuous)):AnyShape(Circle())).stroke(Color.gray.opacity(0.8),lineWidth:1);if visual.image==nil{Text(visual.text).minimumScaleFactor(0.3).foregroundStyle(Color(hex:visual.foreground))}}.aspectRatio(1,contentMode:.fit)}}
private struct RotaryCell:View {let visual:KeyVisual;let internet:String;let serial:String;let rows:Int;let columns:Int;let index:Int;let interactive:Bool;var body:some View{ZStack{KeyFace(visual:visual,round:false);if interactive{HStack(spacing:0){Button(intent:CompanionRotateIntent(internet:internet,serial:serial,rows:rows,columns:columns,index:index,direction:-1)){Color.clear};Button(intent:CompanionPressIntent(internet:internet,local:"",serial:serial,rows:rows,columns:columns,index:index)){Color.clear};Button(intent:CompanionRotateIntent(internet:internet,serial:serial,rows:rows,columns:columns,index:index,direction:1)){Color.clear}}.buttonStyle(.plain)}}}}

struct CompanionWidget1x1:Widget {
    var body:some WidgetConfiguration { AppIntentConfiguration(kind:"Companion1x1",intent:CompanionWidgetIntent.self,provider:Provider1x1()){CompanionGrid(entry:$0,rows:1,columns:1)}.configurationDisplayName("Companion 1×1").description("直接操作 Companion，不會開啟 App。").supportedFamilies([.systemSmall]).contentMarginsDisabled() }
}
struct CompanionWidget3x2:Widget {
    var body:some WidgetConfiguration { AppIntentConfiguration(kind:"Companion3x2",intent:CompanionWidgetIntent.self,provider:Provider3x2()){CompanionGrid(entry:$0,rows:2,columns:3)}.configurationDisplayName("Companion 3×2").description("直接操作 Companion，不會開啟 App。").supportedFamilies([.systemMedium]).contentMarginsDisabled() }
}
struct CompanionWidget4x4:Widget {
    var body:some WidgetConfiguration { AppIntentConfiguration(kind:"Companion4x4",intent:CompanionWidgetIntent.self,provider:Provider4x4()){CompanionGrid(entry:$0,rows:4,columns:4)}.configurationDisplayName("Companion 4×4").description("直接操作 Companion，不會開啟 App。").supportedFamilies([.systemLarge]).contentMarginsDisabled() }
}
struct CompanionWidgetRotary1x1:Widget {var body:some WidgetConfiguration{AppIntentConfiguration(kind:"CompanionRotary1x1",intent:CompanionWidgetIntent.self,provider:ProviderRotary1x1()){CompanionGrid(entry:$0,rows:1,columns:1,rotaryRows:[0],surfaceTag:"rotary")}.configurationDisplayName("旋鈕 1×1").description("Satellite 旋鈕：左轉、按下、右轉。").supportedFamilies([.systemSmall]).contentMarginsDisabled()}}
struct CompanionWidgetMixed4x4:Widget {var body:some WidgetConfiguration{AppIntentConfiguration(kind:"CompanionMixed4x4",intent:CompanionWidgetIntent.self,provider:ProviderMixed4x4()){CompanionGrid(entry:$0,rows:4,columns:4,rotaryRows:[3],surfaceTag:"mixed")}.configurationDisplayName("Companion 4×4＋旋鈕").description("最後一列為 Satellite 旋鈕。").supportedFamilies([.systemLarge]).contentMarginsDisabled()}}

private extension Color {init(hex:String){let s=hex.trimmingCharacters(in:CharacterSet(charactersIn:"#"));var n:UInt64=0;Scanner(string:s).scanHexInt64(&n);self.init(red:Double((n>>16)&255)/255,green:Double((n>>8)&255)/255,blue:Double(n&255)/255)}}

@available(iOSApplicationExtension 18.0, *) struct CompanionControlWidget:ControlWidget {
    static let kind="org.theoakhouse.companion.direct-control"
    var body:some ControlWidgetConfiguration {
        AppIntentControlConfiguration(kind:Self.kind,intent:CompanionControlIntent.self){configuration in
            ControlWidgetButton(action:configuration){
                Label {
                    Text(configuration.control?.name ?? "尚未設定")
                } icon: {
                    if configuration.control?.icon == "companion.logo" {
                        Image("CompanionControlIcon")
                    } else {
                        Image(systemName:configuration.control?.icon ?? "button.programmable")
                    }
                }
            }
        }.displayName("Companion 直接控制").description("直接觸發指定按鈕，不開啟 App。").promptsForUserConfiguration()
    }
}

@main struct CompanionWidgetsBundle:WidgetBundle {
    var body:some Widget {
        CompanionWidget1x1()
        CompanionWidget3x2()
        CompanionWidget4x4()
        CompanionWidgetRotary1x1()
        CompanionWidgetMixed4x4()
        if #available(iOSApplicationExtension 18.0,*) { CompanionControlWidget() }
    }
}
