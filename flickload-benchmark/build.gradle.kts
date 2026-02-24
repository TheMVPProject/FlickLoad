plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.benchmark)
}

android {
    namespace = "io.github.mohidsk.flickload.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    androidTestImplementation(project(":flickload-core"))
    androidTestImplementation(libs.androidx.benchmark.micro)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
