import SwiftUI
import WebKit

@main
struct TuneGameApp: App {
    var body: some Scene { WindowGroup { ServerView() } }
}

struct ServerView: View {
    @AppStorage("serverURL") private var serverURL = ""
    @State private var destination: URL?
    @State private var error = ""
    @State private var reload = 0
    @State private var pendingPath: String?

    var body: some View {
        NavigationStack {
            Group {
                if let destination {
                    VStack(spacing: 0) {
                        if !error.isEmpty {
                            Text(error).foregroundStyle(.red).padding().accessibilityLabel(error)
                        }
                        GameWebView(url: destination, reload: reload, error: $error)
                            .id(destination)
                    }
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Server") { self.destination = nil; error = "" }
                        }
                        ToolbarItem(placement: .topBarTrailing) {
                            Menu("Games", systemImage: "gamecontroller") {
                                Button("Main game") { pendingPath = "/play/" }
                                Button("Private rooms") { pendingPath = "/rooms/" }
                                Button("Reload", systemImage: "arrow.clockwise") { reload += 1 }
                            }
                        }
                    }
                } else {
                    Form {
                        Section {
                            Text("Play the main game or join friends in a private room. Connect to your TuneGame server to begin.")
                            TextField("https://your-server.example", text: $serverURL)
                                .textContentType(.URL)
                                .keyboardType(.URL)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .accessibilityLabel("Private server address")
                            Button("Main game") { connect(path: "/play/") }
                            Button("Private rooms") { connect(path: "/rooms/") }
                        }
                        if !error.isEmpty { Text(error).foregroundStyle(.red) }
                    }
                }
            }
            .confirmationDialog("Switch games? The current screen will restart. Leave your private room first if you want to leave it.", isPresented: Binding(get: { pendingPath != nil }, set: { if !$0 { pendingPath = nil } }), titleVisibility: .visible) {
                Button("Switch games") {
                    if let path = pendingPath { connect(path: path) }
                    pendingPath = nil
                }
                Button("Cancel", role: .cancel) { pendingPath = nil }
            }
            .navigationTitle("TuneGame")
            .navigationBarTitleDisplayMode(.inline)
            .tint(Color(red: 0.11, green: 0.72, blue: 0.33))
        }
    }

    private func connect(path: String) {
        guard var parts = URLComponents(string: serverURL.trimmingCharacters(in: .whitespacesAndNewlines)),
              let host = parts.host, !host.isEmpty,
              parts.user == nil, parts.password == nil,
              parts.query == nil, parts.fragment == nil,
              parts.path.isEmpty || parts.path == "/" else {
            error = "Enter an HTTPS server address without a path."
            return
        }
        var allowed = parts.scheme == "https"
        #if DEBUG
        allowed = allowed || (parts.scheme == "http" && ["localhost", "127.0.0.1"].contains(host))
        #endif
        guard allowed else { error = "Use HTTPS to connect to your private server."; return }
        parts.path = path
        guard let url = parts.url else { error = "Check the server address."; return }
        error = ""
        reload = 0
        destination = url
    }
}

struct GameWebView: UIViewRepresentable {
    let url: URL
    let reload: Int
    @Binding var error: String

    func makeCoordinator() -> Coordinator { Coordinator(url: url, error: $error) }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.navigationDelegate = context.coordinator
        web.isOpaque = false
        web.load(URLRequest(url: url))
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        if context.coordinator.reload != reload {
            context.coordinator.reload = reload
            web.load(URLRequest(url: url))
        }
    }

    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        web.stopLoading()
        web.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        let url: URL
        var reload = 0
        @Binding var error: String
        init(url: URL, error: Binding<String>) { self.url = url; _error = error }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let target = action.request.url,
                  target.scheme == url.scheme, target.host == url.host, target.port == url.port,
                  ["/rooms", "/play"].contains(target.path) || target.path.hasPrefix("/rooms/") || target.path.hasPrefix("/play/") else { decisionHandler(.cancel); return }
            decisionHandler(.allow)
        }
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { error = "" }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError failure: Error) {
            error = "Cannot connect to the game server. Check the address and connection, then reload."
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError failure: Error) {
            error = "Connection interrupted. Reload to reconnect to your room."
        }
        func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse,
                     decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
            if let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
                error = "Server returned \(http.statusCode). Check the address or reload."
                decisionHandler(.cancel)
            } else { decisionHandler(.allow) }
        }
    }
}
