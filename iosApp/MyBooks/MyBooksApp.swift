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
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
