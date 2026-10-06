plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; this plugin also pins the Kotlin version it uses.
    alias(libs.plugins.kotlin.compose) apply false
}
