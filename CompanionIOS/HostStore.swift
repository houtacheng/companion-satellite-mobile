import Foundation
import Network
import UniformTypeIdentifiers
import SwiftUI
import WidgetKit

struct CompanionHost: Identifiable, Codable, Equatable {
    var id = UUID()
    var name: String
    var internetURL: String
    var localURL: String
    var satelliteURL: String {
        guard let u = URL(string: internetURL), let host = u.host else { return "" }
        let port = u.port.map { ":\($0)" } ?? ""
        return "wss://\(host)\(port)/satellite"
    }
    var localSatelliteHost: String? { URL(string: localURL)?.host }
}

@MainActor final class HostStore: ObservableObject {
    @Published var hosts: [CompanionHost] = [] { didSet { save() } }
    @Published var selectedID: UUID?
    private let key = "companion-ios-hosts-v1"
    private let shared = UserDefaults(suiteName:"group.org.theoakhouse.companion")!
    init() { let d=shared.data(forKey:key) ?? UserDefaults.standard.data(forKey:key);if let d,let h=try? JSONDecoder().decode([CompanionHost].self,from:d){hosts=h;selectedID=h.first?.id;shared.set(d,forKey:key)} }
    var selected: CompanionHost? { hosts.first { $0.id == selectedID } }
    func add(_ host: CompanionHost){hosts.append(host);selectedID=host.id}
    func delete(at offsets: IndexSet){hosts.remove(atOffsets:offsets);if !hosts.contains(where:{$0.id==selectedID}){selectedID=hosts.first?.id}}
    func importCSV(_ text:String)->(added:Int,duplicates:Int,invalid:Int){var added=0,duplicates=0,invalid=0;let rows=CSVCodec.portableRows(text);for (i,row) in rows.enumerated(){let first=row.first?.trimmingCharacters(in:CharacterSet(charactersIn:"\u{feff}")).lowercased();if i==0,first=="name"{continue};guard row.count>=2 else{invalid += 1;continue};let name=row[0].trimmingCharacters(in:.whitespacesAndNewlines),internet=Self.normalized(row[1]),local=row.count>2 ? Self.normalized(row[2]):"";guard !name.isEmpty,!internet.isEmpty,URL(string:internet)?.host != nil else{invalid += 1;continue};let key=Self.key(internet);if hosts.contains(where:{Self.key($0.internetURL)==key}){duplicates += 1;continue};hosts.append(CompanionHost(name:name,internetURL:internet,localURL:local));added += 1};if selectedID==nil{selectedID=hosts.first?.id};return(added,duplicates,invalid)}
    func exportCSV()->String{CSVCodec.make([["name","internet_url","local_url"]]+hosts.map{[$0.name,$0.internetURL,$0.localURL]})}
    private static func normalized(_ value:String)->String{let v=value.trimmingCharacters(in:.whitespacesAndNewlines);if v.isEmpty{return ""};return v.contains("://") ? v:"http://"+v}
    private static func key(_ value:String)->String{value.trimmingCharacters(in:CharacterSet(charactersIn:"/ ")).lowercased()}
    private func save(){if let d=try? JSONEncoder().encode(hosts){shared.set(d,forKey:key);UserDefaults.standard.set(d,forKey:key)}}
}

enum CSVCodec {static func rows(_ text:String)->[[String]]{var rows:[[String]]=[],row:[String]=[],field="";var quoted=false;let chars=Array(text);var i=0;while i<chars.count{let ch=chars[i];if quoted{if ch=="\""{if i+1<chars.count,chars[i+1]=="\""{field.append("\"");i += 1}else{quoted=false}}else{field.append(ch)}}else if ch=="\""{quoted=true}else if ch==","{row.append(field);field=""}else if ch=="\n"{row.append(field.trimmingCharacters(in:CharacterSet(charactersIn:"\r")));if !row.allSatisfy({$0.isEmpty}){rows.append(row)};row=[];field=""}else{field.append(ch)};i += 1};row.append(field.trimmingCharacters(in:CharacterSet(charactersIn:"\r")));if !row.allSatisfy({$0.isEmpty}){rows.append(row)};return rows};static func make(_ rows:[[String]])->String{rows.map{$0.map{v in "\""+v.replacingOccurrences(of:"\"",with:"\"\"")+"\""}.joined(separator:",")}.joined(separator:"\r\n")+"\r\n"}}
extension CSVCodec {static func portableRows(_ text:String)->[[String]]{text.replacingOccurrences(of:"\r\n",with:"\n").replacingOccurrences(of:"\r",with:"\n").split(separator:"\n",omittingEmptySubsequences:true).map{parseLine(String($0))}};private static func parseLine(_ line:String)->[String]{var values:[String]=[],field="";var quoted=false;let chars=Array(line);var i=0;while i<chars.count{let ch=chars[i];if ch=="\""{if quoted,i+1<chars.count,chars[i+1]=="\""{field.append("\"");i += 1}else{quoted.toggle()}}else if ch==",",!quoted{values.append(field);field=""}else{field.append(ch)};i += 1};values.append(field);return values}}
struct HostCSVDocument:FileDocument {static var readableContentTypes:[UTType]{[.commaSeparatedText]};var text:String;init(text:String){self.text=text};init(configuration:ReadConfiguration)throws{text=String(data:configuration.file.regularFileContents ?? Data(),encoding:.utf8) ?? ""};func fileWrapper(configuration:WriteConfiguration)throws->FileWrapper{FileWrapper(regularFileWithContents:Data(text.utf8))}}
enum CSVImportError:LocalizedError {case empty,encoding,noRecords;var errorDescription:String?{switch self{case .empty:return "選取的 CSV 是空白檔案，請重新匯出後再試一次。";case .encoding:return "無法辨識 CSV 編碼，請儲存為 UTF-8 或 UTF-16。";case .noRecords:return "CSV 中沒有可辨識的主機資料。標題必須為 name,internet_url,local_url。"}}}

final class NetworkState: ObservableObject {
    @Published var wifi = false
    private let monitor=NWPathMonitor();private let queue=DispatchQueue(label:"companion.path")
    init(){monitor.pathUpdateHandler={p in DispatchQueue.main.async{self.wifi=p.status == .satisfied && p.usesInterfaceType(.wifi)}};monitor.start(queue:queue)}
    deinit{monitor.cancel()}
}

struct CompanionControlPreset:Identifiable,Codable,Equatable {var id=UUID();var name:String;var icon:String;var hostID:UUID;var customIconFile:String?=nil;var customIconBase64:String?=nil}
enum ControlIconFiles {
    private static var directory:URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier:"group.org.theoakhouse.companion")?.appendingPathComponent("ControlIcons",isDirectory:true) }
    static func save(data:Data,id:UUID)throws->(name:String,data:Data){guard let source=UIImage(data:data),let directory else{throw CocoaError(.fileReadCorruptFile)};let side:CGFloat=96;let renderer=UIGraphicsImageRenderer(size:CGSize(width:side,height:side),format:{let f=UIGraphicsImageRendererFormat();f.opaque=false;f.scale=1;return f}());let prepared=renderer.image{_ in let scale=min(side/source.size.width,side/source.size.height);let size=CGSize(width:source.size.width*scale,height:source.size.height*scale);source.draw(in:CGRect(x:(side-size.width)/2,y:(side-size.height)/2,width:size.width,height:size.height))};guard let png=prepared.pngData()else{throw CocoaError(.fileWriteUnknown)};try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true);let name="\(id.uuidString).png";try png.write(to:directory.appendingPathComponent(name),options:.atomic);return(name,png)}
    static func image(named:String?)->UIImage?{guard let named,let directory else{return nil};return UIImage(contentsOfFile:directory.appendingPathComponent(named).path)}
    static func remove(named:String?){guard let named,let directory else{return};try? FileManager.default.removeItem(at:directory.appendingPathComponent(named))}
}
@MainActor final class ControlPresetStore:ObservableObject {
    @Published var presets:[CompanionControlPreset]=[] { didSet { save() } }
    private let key="companion-ios-control-presets-v1"
    private let shared=UserDefaults(suiteName:"group.org.theoakhouse.companion")!
    init(){if let data=shared.data(forKey:key),let value=try? JSONDecoder().decode([CompanionControlPreset].self,from:data){presets=value}}
    func save(){
        if let data=try? JSONEncoder().encode(presets){
            shared.set(data,forKey:key)
            shared.synchronize()
            if #available(iOS 18.0,*) { ControlCenter.shared.reloadControls(ofKind:"org.theoakhouse.companion.direct-control") }
        }
    }
    func remove(id:UUID){guard let value=presets.first(where:{$0.id==id})else{return};ControlIconFiles.remove(named:value.customIconFile);presets.removeAll{$0.id==id}}
    func delete(at offsets:IndexSet){for index in offsets.sorted(by:>){let value=presets[index];ControlIconFiles.remove(named:value.customIconFile);presets.remove(at:index)}}
}
