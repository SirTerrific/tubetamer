plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sirterrific.tubetamer.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.datastore.preferences)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
    api(libs.coil.compose)
    api(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
