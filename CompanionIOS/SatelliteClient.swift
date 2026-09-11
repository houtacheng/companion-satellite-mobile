import Foundation
import Network
import SwiftUI

struct SatelliteKeyState { var text="";var background=Color.black;var foreground=Color.white;var image:UIImage? }

@MainActor final class SatelliteClient:ObservableObject {
    @Published var keys=Array(repeating:SatelliteKeyState(),count:16)
    @Published var status="離線"
    private let host:CompanionHost;private var task:URLSessionWebSocketTask?;private var tcp:NWConnection?;private var ping:Task<Void,Never>?;private var locations:[String:String]=[:];private let device="ios-\(UUID().uuidString)"
    init(host:CompanionHost){self.host=host}
    func start(wifi:Bool){stop();status="連線中";if wifi,let h=host.localSatelliteHost{startTCP(h)}else{startWebSocket()}}
    func stop(){ping?.cancel();ping=nil;task?.cancel(with:.goingAway,reason:nil);task=nil;tcp?.cancel();tcp=nil;status="離線"}
    private func startWebSocket(){guard let u=URL(string:host.satelliteURL)else{return};let t=URLSession.shared.webSocketTask(with:u);task=t;t.resume();status="網際網路連線中";receiveWS(t);beginPing()}
    private func receiveWS(_ t:URLSessionWebSocketTask){t.receive{[weak self] result in Task{@MainActor in guard let self else{return};switch result{case .success(let m):if case .string(let s)=m{self.handleLines(s)};self.receiveWS(t);case .failure(let e):self.status="離線：\(e.localizedDescription)"}}}}
    private func startTCP(_ h:String){let c=NWConnection(host:NWEndpoint.Host(h),port:16622,using:.tcp);tcp=c;c.stateUpdateHandler={[weak self] state in Task{@MainActor in guard let self else{return};if case .ready=state{self.status="區網已連線";self.receiveTCP(c);self.beginPing()}else if case .failed(let e)=state{self.status="區網失敗，改走網際網路";c.cancel();self.tcp=nil;self.startWebSocket()}}};c.start(queue:.global())}
    private func receiveTCP(_ c:NWConnection){c.receive(minimumIncompleteLength:1,maximumLength:65536){[weak self] data,_,_,error in Task{@MainActor in guard let self else{return};if let data,let s=String(data:data,encoding:.utf8){self.handleLines(s)};if error==nil{self.receiveTCP(c)}}}}
    private func beginPing(){ping=Task{while !Task.isCancelled{send("PING");try? await Task.sleep(for:.seconds(1))}}}
    private func send(_ line:String){let data=Data((line+"\n").utf8);if let task{task.send(.string(line+"\n")){_ in}}else{tcp?.send(content:data,completion:.contentProcessed({_ in}))}}
    private func handleLines(_ value:String){for line in value.split(whereSeparator:{$0=="\n"||$0=="\r"}).map(String.init){if line.hasPrefix("BEGIN")||line.hasPrefix("CAPS"){addDevice()}else if line.hasPrefix("PING"){send("PONG")}else if line.hasPrefix("ADD-DEVICE"),line.contains(device){status="Satellite Online"}else if line.hasPrefix("KEY-STATE"),line.contains(device){parseState(line)}}}
    private func params(_ line:String)->[String:String]{var out:[String:String]=[:];let pattern=#"([A-Za-z_]+)=(?:\"([^\"]*)\"|(\S+))"#;guard let r=try? NSRegularExpression(pattern:pattern)else{return out};let ns=line as NSString;for m in r.matches(in:line,range:NSRange(location:0,length:ns.length)){let k=ns.substring(with:m.range(at:1)).uppercased();let range=m.range(at:2).location != NSNotFound ? m.range(at:2):m.range(at:3);out[k]=ns.substring(with:range)};return out}
    private func parseState(_ line:String){let p=params(line);let parts=(p["KEY"] ?? p["CONTROLID"] ?? "0/0").split(separator:"/");guard parts.count>=2,let r=Int(parts[0]),let c=Int(parts[1]),(0..<16).contains(r*4+c)else{return};let i=r*4+c;var k=keys[i];if let t=p["TEXT"],let d=Data(base64Encoded:t){k.text=String(data:d,encoding:.utf8) ?? ""};k.background=Color(hex:p["COLOR"] ?? "000000");k.foreground=Color(hex:p["TEXTCOLOR"] ?? "ffffff");if let b=p["BITMAP"]?.split(separator:",").last,let d=Data(base64Encoded:String(b)){k.image=UIImage(data:d)};keys[i]=k;if let l=p["LOCATION"]{locations["\(r)/\(c)"]=l};status="Satellite Online"}
    private func addDevice(){let controls=Dictionary(uniqueKeysWithValues:(0..<16).map{("\($0/4)/\($0%4)",["row":$0/4,"column":$0%4])});let manifest:[String:Any]=["stylePresets":["default":["bitmap":["w":288,"h":232],"text":true,"textStyle":true,"colors":"hex"]],"controls":controls];guard let d=try? JSONSerialization.data(withJSONObject:manifest)else{return};let layout=d.base64EncodedString();send("ADD-DEVICE DEVICEID=\"\(device)\" LAYOUT_MANIFEST=\"\(layout)\" PRODUCT_NAME=\"iPhone Companion 4x4\" VARIABLES=\"W10=\" BRIGHTNESS=0 PINCODE_LOCK=\"FULL\" SERIAL=\"\(device)\" SERIAL_IS_UNIQUE=1 BITMAP_FORMAT=\"png\"")}
    func press(_ index:Int){let key="\(index/4)/\(index%4)";send("KEY-PRESS DEVICEID=\"\(device)\" CONTROLID=\"\(key)\" KEY=\"\(key)\" PRESSED=1");Task{try? await Task.sleep(for:.milliseconds(80));send("KEY-PRESS DEVICEID=\"\(device)\" CONTROLID=\"\(key)\" KEY=\"\(key)\" PRESSED=0")}}
}

extension Color { init(hex:String){let s=hex.trimmingCharacters(in:CharacterSet(charactersIn:"#"));var n:UInt64=0;Scanner(string:s).scanHexInt64(&n);self.init(red:Double((n>>16)&255)/255,green:Double((n>>8)&255)/255,blue:Double(n&255)/255)} }

struct SatelliteGridView:View {
    let host:CompanionHost;@StateObject private var client:SatelliteClient;@StateObject private var network=NetworkState()
    init(host:CompanionHost){self.host=host;_client=StateObject(wrappedValue:SatelliteClient(host:host))}
    var body: some View {
        VStack(spacing: 8) {
            Text(client.status).font(.caption).foregroundStyle(client.status.contains("Online") ? Color.green : Color.secondary)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                ForEach(0..<16, id: \.self) { index in
                    Button { client.press(index) } label: {
                        ZStack {
                            if let image = client.keys[index].image { Image(uiImage: image).resizable().scaledToFit() }
                            else { client.keys[index].background; Text(client.keys[index].text).foregroundStyle(client.keys[index].foreground).font(.headline).minimumScaleFactor(0.35) }
                        }.frame(maxWidth: .infinity).aspectRatio(288.0 / 232.0, contentMode: .fit).clipped().clipShape(RoundedRectangle(cornerRadius: 10,style:.continuous)).overlay(RoundedRectangle(cornerRadius: 10,style:.continuous).stroke(.gray.opacity(0.6),lineWidth:1))
                    }.buttonStyle(.plain)
                }
            }
            Spacer()
        }.padding(8).navigationTitle(host.name).onAppear { client.start(wifi: network.wifi) }.onChange(of: network.wifi) { _, value in client.start(wifi: value) }.onDisappear { client.stop() }
    }
}
