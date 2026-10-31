import SwiftUI
import Shared

@main
struct MyBooksApp: App {
    var body: some Scene {
        WindowGroup {
            // The shared Compose UI handles safe areas and the keyboard itself.
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    /// Kept for the app's lifetime: it holds the pending scanner/camera/picker callbacks.
    private static let nativeServices = NativeServices()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(native: Self.nativeServices)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
