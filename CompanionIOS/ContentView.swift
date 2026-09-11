import SwiftUI
import WebKit
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject var store: HostStore
    @StateObject private var network = NetworkState()
    @State private var showingHosts=false
    @State private var showingControls=false
    var body: some View {
        NavigationStack {
            Group {
                if let host=store.selected { CompanionWebView(urlString: network.wifi && !host.localURL.isEmpty ? host.localURL : host.internetURL).id("\(host.id)-\(network.wifi)") }
                else { ContentUnavailableView("尚未設定 Companion 主機",systemImage:"server.rack",description:Text("點選右上角「主機」新增主機")) }
            }
            .navigationTitle(store.selected?.name ?? "Companion")
            .toolbar {
                ToolbarItemGroup(placement:.topBarLeading){
                    Button("主機"){showingHosts=true}
                    Button("控制項"){showingControls=true}
                }
            }
            .sheet(isPresented:$showingHosts){HostListView().environmentObject(store)}
            .sheet(isPresented:$showingControls){ControlPresetListView().environmentObject(store)}
        }
    }
}

struct CompanionWebView: UIViewRepresentable {
    let urlString:String
    func makeUIView(context:Context)->WKWebView { let c=WKWebViewConfiguration();c.websiteDataStore = .default();return WKWebView(frame:.zero,configuration:c) }
    func updateUIView(_ web:WKWebView,context:Context){guard let u=URL(string:urlString),web.url != u else{return};web.load(URLRequest(url:u))}
}

struct HostListView: View {
    @EnvironmentObject var store:HostStore
    @Environment(\.dismiss) var dismiss
    @State private var editing:CompanionHost?
    @State private var importing=false
    @State private var exporting=false
    @State private var exportDocument=HostCSVDocument(text:"")
    @State private var resultMessage:String?
    @State private var connection:[UUID:HostConnectionState]=[:]
    @StateObject private var network=NetworkState()
    var body: some View {
        NavigationStack {
            List {
                ForEach(store.hosts) { host in
                    Button { store.selectedID = host.id; dismiss() } label: {
                        HStack {
                            Image(systemName: store.selectedID == host.id ? "checkmark.circle.fill" : "circle").foregroundStyle(store.selectedID == host.id ? Color.green : Color.secondary)
                            VStack(alignment: .leading) { Text(host.name); Text(host.internetURL).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            let state=connection[host.id] ?? .checking
                            VStack(alignment:.trailing,spacing:2){Image(systemName:state.icon).foregroundStyle(state.color);Text(state.label).font(.caption2).foregroundStyle(.secondary)}
                        }
                    }.swipeActions { Button("編輯") { editing = host }.tint(.blue) }
                }.onDelete(perform: store.delete)
            }.navigationTitle("Companion 主機").toolbar {
                ToolbarItemGroup(placement:.topBarTrailing){Menu{Button("匯入主機 CSV",systemImage:"square.and.arrow.down"){importing=true};Button("匯出主機 CSV",systemImage:"square.and.arrow.up"){exportDocument=HostCSVDocument(text:store.exportCSV());DispatchQueue.main.async{exporting=true}}}label:{Image(systemName:"ellipsis.circle")};Button { editing = CompanionHost(name: "", internetURL: "", localURL: "") } label: { Image(systemName: "plus") }}
                ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } }
            }.sheet(item: $editing) { host in
                HostEditor(host: host) { updated in
                    if let index = store.hosts.firstIndex(where: { $0.id == updated.id }) { store.hosts[index] = updated } else { store.add(updated) }
                    editing = nil
                }
            }
            .fileImporter(isPresented:$importing,allowedContentTypes:[.commaSeparatedText,.plainText]){result in do{let url=try result.get();let scoped=url.startAccessingSecurityScopedResource();defer{if scoped{url.stopAccessingSecurityScopedResource()}};let data=try Data(contentsOf:url);guard !data.isEmpty else{throw CSVImportError.empty};let text=String(data:data,encoding:.utf8) ?? String(data:data,encoding:.utf16) ?? String(data:data,encoding:.utf16LittleEndian) ?? String(data:data,encoding:.utf16BigEndian);guard let text,!text.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty else{throw CSVImportError.encoding};let report=store.importCSV(text);guard report.added+report.duplicates+report.invalid>0 else{throw CSVImportError.noRecords};resultMessage="已匯入 \(report.added) 台；略過重複 \(report.duplicates) 台；無效 \(report.invalid) 筆"}catch{resultMessage="匯入失敗：\(error.localizedDescription)"}}
            .fileExporter(isPresented:$exporting,document:exportDocument,contentType:.commaSeparatedText,defaultFilename:"companion-hosts.csv"){result in if case .failure(let error)=result{resultMessage="匯出失敗：\(error.localizedDescription)"}}
            .alert("主機 CSV",isPresented:Binding(get:{resultMessage != nil},set:{if !$0{resultMessage=nil}})){Button("確定",role:.cancel){}}message:{Text(resultMessage ?? "")}
            .task(id:"\(store.hosts.map(\.id))\(network.wifi)"){await refreshConnections()}
        }
    }
    private func refreshConnections()async{for host in store.hosts{connection[host.id] = .checking;if network.wifi,!host.localURL.isEmpty,await reachable(host.localURL){connection[host.id] = .local}else if await reachable(host.internetURL){connection[host.id] = .internet}else{connection[host.id] = .offline}}}
    private func reachable(_ value:String)async->Bool{guard let url=URL(string:value)else{return false};var request=URLRequest(url:url);request.timeoutInterval = 2;request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData;do{let (_,response)=try await URLSession.shared.data(for:request);return (response as? HTTPURLResponse).map{(100..<500).contains($0.statusCode)} ?? true}catch{return false}}
}

struct ControlPresetListView:View {@EnvironmentObject var hosts:HostStore;@Environment(\.dismiss)var dismiss;@StateObject private var store=ControlPresetStore();@State private var editing:CompanionControlPreset?;@State private var deleting:CompanionControlPreset?;var body:some View{NavigationStack{List{ForEach(store.presets){preset in HStack{Button{editing=preset}label:{HStack{ControlPresetIcon(preset:preset,size:28);VStack(alignment:.leading){Text(preset.name);Text(hosts.hosts.first(where:{$0.id==preset.hostID})?.name ?? "主機已刪除").font(.caption).foregroundStyle(.secondary)};Spacer()}}.buttonStyle(.plain);Button(role:.destructive){deleting=preset}label:{Image(systemName:"trash").foregroundStyle(.red)}.buttonStyle(.borderless)}}.onDelete(perform:store.delete)}.navigationTitle("控制項").toolbar{ToolbarItem(placement:.cancellationAction){Button("完成"){dismiss()}};ToolbarItem(placement:.primaryAction){Button{if let host=hosts.hosts.first{editing=CompanionControlPreset(name:"Companion",icon:"button.programmable",hostID:host.id)}}label:{Image(systemName:"plus")}.disabled(hosts.hosts.isEmpty)}}.sheet(item:$editing){preset in ControlPresetEditor(preset:preset,hosts:hosts.hosts){value in if let i=store.presets.firstIndex(where:{$0.id==value.id}){let old=store.presets[i];if old.customIconFile != value.customIconFile{ControlIconFiles.remove(named:old.customIconFile)};store.presets[i]=value}else{store.presets.append(value)};editing=nil}}.confirmationDialog("移除此控制項？",isPresented:Binding(get:{deleting != nil},set:{if !$0{deleting=nil}}),titleVisibility:.visible){Button("移除控制項",role:.destructive){if let deleting{store.remove(id:deleting.id)};deleting=nil};Button("取消",role:.cancel){deleting=nil}}}}}

struct ControlPresetEditor:View {
    @State var preset:CompanionControlPreset;let hosts:[CompanionHost];let save:(CompanionControlPreset)->Void
    @Environment(\.dismiss)var dismiss
    @State private var importingIcon=false
    @State private var iconError:String?
    private let icons=["button.programmable","lightbulb.fill","fan.fill","door.left.hand.open","speaker.wave.2.fill","play.fill","stop.fill","power","bolt.fill","circle.grid.2x2.fill"]
    var body:some View{NavigationStack{Form{TextField("控制項文字",text:$preset.name);Picker("Companion 主機",selection:$preset.hostID){ForEach(hosts){Text($0.name).tag($0.id)}};Section("圖示"){HStack{Spacer();ControlPresetIcon(preset:preset,size:64);Spacer()};Picker("控制中心圖示",selection:$preset.icon){ForEach(icons,id:\.self){Label($0,systemImage:$0).tag($0)}};TextField("SF Symbol 名稱",text:$preset.icon).textInputAutocapitalization(.never);Button("自訂匯入 PNG 檔",systemImage:"photo.badge.plus"){importingIcon=true};Text("自訂 PNG 顯示於 App；iOS 控制中心會使用上方選定的 Symbol 圖示。").font(.caption).foregroundStyle(.secondary);if preset.customIconFile != nil{Button("移除自訂圖示",role:.destructive){ControlIconFiles.remove(named:preset.customIconFile);preset.customIconFile=nil;preset.customIconBase64=nil}}}}.navigationTitle("控制中心設定").toolbar{ToolbarItem(placement:.cancellationAction){Button("取消"){dismiss()}};ToolbarItem(placement:.confirmationAction){Button("儲存"){save(preset)}.disabled(preset.name.trimmingCharacters(in:.whitespaces).isEmpty)}}.fileImporter(isPresented:$importingIcon,allowedContentTypes:[.png]){result in do{let url=try result.get();let scoped=url.startAccessingSecurityScopedResource();defer{if scoped{url.stopAccessingSecurityScopedResource()}};let data=try Data(contentsOf:url);let saved=try ControlIconFiles.save(data:data,id:preset.id);preset.customIconFile=saved.name;preset.customIconBase64=saved.data.base64EncodedString()}catch{iconError=error.localizedDescription}}.alert("無法匯入 PNG",isPresented:Binding(get:{iconError != nil},set:{if !$0{iconError=nil}})){Button("確定",role:.cancel){}}message:{Text(iconError ?? "")}}}
}

private struct ControlPresetIcon:View {let preset:CompanionControlPreset;let size:CGFloat;var body:some View{Group{if let image=ControlIconFiles.image(named:preset.customIconFile){Image(uiImage:image).resizable().scaledToFit()}else{Image(systemName:preset.icon).resizable().scaledToFit()}}.frame(width:size,height:size).clipShape(RoundedRectangle(cornerRadius:size*0.18,style:.continuous))}}

private enum HostConnectionState {case checking,local,internet,offline;var icon:String{switch self{case .checking:return "clock";case .local:return "wifi.circle.fill";case .internet:return "globe.americas.fill";case .offline:return "exclamationmark.circle.fill"}};var label:String{switch self{case .checking:return "檢查中";case .local:return "區網";case .internet:return "網際網路";case .offline:return "離線"}};var color:Color{switch self{case .checking:return .secondary;case .local,.internet:return .green;case .offline:return .red}}}

struct HostEditor:View {
    @State var host:CompanionHost;let save:(CompanionHost)->Void
    @Environment(\.dismiss) var dismiss
    var body:some View{NavigationStack{Form{TextField("名稱，例如：家裡",text:$host.name);TextField("網際網路網址",text:$host.internetURL).textInputAutocapitalization(.never).keyboardType(.URL);TextField("區網網址",text:$host.localURL).textInputAutocapitalization(.never).keyboardType(.URL);Section("自動產生 Satellite 網址"){Text(host.satelliteURL).font(.caption)}}.navigationTitle("主機設定").toolbar{ToolbarItem(placement:.confirmationAction){Button("儲存"){host.internetURL=normalized(host.internetURL);host.localURL=normalized(host.localURL);save(host)}.disabled(host.name.trimmingCharacters(in:.whitespaces).isEmpty || host.internetURL.isEmpty)};ToolbarItem(placement:.cancellationAction){Button("取消"){dismiss()}}}}}
    private func normalized(_ s:String)->String{let v=s.trimmingCharacters(in:.whitespacesAndNewlines);if v.isEmpty{return ""};return v.contains("://") ? v:"http://"+v}
}
