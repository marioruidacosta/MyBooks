// The nightly job that turns the National Library's new legal-deposit records into monthly lists by genre
// (see docs/superpowers/specs/2026-11-02-new-in-portugal-design.md). Runs on GitHub Actions; not part of the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.mcosta.mybooks.releases.MainKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
