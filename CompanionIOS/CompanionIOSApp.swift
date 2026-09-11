import SwiftUI

@main
struct CompanionIOSApp: App {
    @StateObject private var hosts = HostStore()
    var body: some Scene { WindowGroup { ContentView().environmentObject(hosts) } }
}
