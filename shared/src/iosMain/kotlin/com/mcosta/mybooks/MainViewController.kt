package com.mcosta.mybooks

import androidx.compose.ui.window.ComposeUIViewController
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.db.AppDatabase
import com.mcosta.mybooks.data.db.createDatabase
import com.mcosta.mybooks.platform.IosNativeServices
import com.mcosta.mybooks.platform.iosPlatformServices
import com.mcosta.mybooks.social.SocialConfig
import com.mcosta.mybooks.ui.App
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/** The app's Documents folder (backed up by iOS, survives updates). */
@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String =
    requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)?.path
    ) { "No Documents directory" }

private fun infoString(key: String): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()

/** Created once for the app's lifetime. */
private val container: AppContainer by lazy {
    val documents = documentsPath()
    AppContainer(
        database = createDatabase(Room.databaseBuilder<AppDatabase>(name = "$documents/mybooks.db"), BundledSQLiteDriver()),
        // Sending the sign-in email can take longer than the default wait.
        httpEngine = Darwin.create { configureSession { timeoutIntervalForRequest = 30.0 } },
        coverStore = CoverStore(FileSystem.SYSTEM, "$documents/covers".toPath()),
        googleBooksApiKey = infoString("GoogleBooksApiKey").trim(),
        userAgent = "MyBooks/${infoString("CFBundleShortVersionString")} (iOS; personal library app)",
        socialConfig = SocialConfig(infoString("SupabaseUrl").trim(), infoString("SupabaseAnonKey").trim()),
    )
}

/** Entry point used by the Swift app: the whole shared UI in a UIViewController. */
@Suppress("FunctionName", "unused")
fun MainViewController(native: IosNativeServices): UIViewController {
    val services = iosPlatformServices(native)
    return ComposeUIViewController { App(container, services) }
}
