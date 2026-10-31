import AVFoundation
import Shared
import UIKit
import UniformTypeIdentifiers
import VisionKit

/// iPhone implementations of the scanner, camera and file pickers used by the shared Kotlin UI.
/// Each call reports (result, error) to its completion; both nil means the user cancelled.
final class NativeServices: NSObject, IosNativeServices {
    private var scanCompletion: ((String?, String?) -> Void)?
    private var photoCompletion: ((Data?, String?) -> Void)?
    private var saveCompletion: ((String?, String?) -> Void)?
    private var openCompletion: ((Data?, String?) -> Void)?
    private weak var presented: UIViewController?

    // MARK: - Barcode scanner (VisionKit, iOS 16+)

    func scanBarcode(completion: @escaping (String?, String?) -> Void) {
        guard DataScannerViewController.isSupported else {
            completion(nil, "This iPhone can't scan barcodes")
            return
        }
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                guard granted, DataScannerViewController.isAvailable else {
                    completion(nil, "Allow camera access for MyBooks in Settings to scan barcodes")
                    return
                }
                self.presentScanner(completion: completion)
            }
        }
    }

    private func presentScanner(completion: @escaping (String?, String?) -> Void) {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce])],
            qualityLevel: .balanced,
            isHighlightingEnabled: true
        )
        scanner.delegate = self
        scanner.navigationItem.title = "Scan the barcode"
        scanner.navigationItem.leftBarButtonItem = UIBarButtonItem(
            systemItem: .cancel,
            primaryAction: UIAction { [weak self] _ in self?.finishScan(code: nil, error: nil) }
        )
        scanCompletion = completion
        present(UINavigationController(rootViewController: scanner)) {
            do {
                try scanner.startScanning()
            } catch {
                self.finishScan(code: nil, error: "Couldn't start the camera")
            }
        }
    }

    private func finishScan(code: String?, error: String?) {
        guard let completion = scanCompletion else { return }
        scanCompletion = nil
        dismissPresented { completion(code, error) }
    }

    // MARK: - Camera

    func takePhoto(completion: @escaping (Data?, String?) -> Void) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            completion(nil, "No camera available")
            return
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = self
        photoCompletion = completion
        present(picker)
    }

    // MARK: - Files

    func saveDocument(name: String, data: Data, completion: @escaping (String?, String?) -> Void) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
        } catch {
            completion(nil, "Couldn't prepare the file")
            return
        }
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        picker.delegate = self
        saveCompletion = completion
        present(picker)
    }

    func openDocument(types: [String], completion: @escaping (Data?, String?) -> Void) {
        let contentTypes = types.compactMap { UTType(mimeType: $0) } + [.zip, .json, .data]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: contentTypes, asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        openCompletion = completion
        present(picker)
    }

    // MARK: - Presentation

    private func present(_ controller: UIViewController, completion: (() -> Void)? = nil) {
        guard let top = Self.topViewController() else { return }
        presented = controller
        top.present(controller, animated: true, completion: completion)
    }

    private func dismissPresented(then action: @escaping () -> Void) {
        if let controller = presented, controller.presentingViewController != nil {
            controller.dismiss(animated: true, completion: action)
        } else {
            action()
        }
        presented = nil
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let next = top?.presentedViewController { top = next }
        return top
    }
}

extension NativeServices: DataScannerViewControllerDelegate {
    func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        for item in addedItems {
            if case .barcode(let barcode) = item, let value = barcode.payloadStringValue {
                dataScanner.stopScanning()
                finishScan(code: value, error: nil)
                return
            }
        }
    }
}

extension NativeServices: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let data = (info[.originalImage] as? UIImage)?.jpegData(compressionQuality: 0.9)
        let completion = photoCompletion
        photoCompletion = nil
        dismissPresented { completion?(data, data == nil ? "Couldn't read the photo" : nil) }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        let completion = photoCompletion
        photoCompletion = nil
        dismissPresented { completion?(nil, nil) }
    }
}

extension NativeServices: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        presented = nil
        if let completion = saveCompletion {
            saveCompletion = nil
            completion("saved", nil)
        } else if let completion = openCompletion {
            openCompletion = nil
            guard let url = urls.first, let data = try? Data(contentsOf: url) else {
                completion(nil, "Couldn't read the file")
                return
            }
            completion(data, nil)
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        presented = nil
        if let completion = saveCompletion {
            saveCompletion = nil
            completion(nil, nil)
        } else if let completion = openCompletion {
            openCompletion = nil
            completion(nil, nil)
        }
    }
}
