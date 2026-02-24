import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.mohidsk.flickload"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.cronet)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(
        variant = "release",
        sourcesJar = true,
        publishJavadocJar = true
    ))

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = property("GROUP").toString(),
        artifactId = "flickload-core",
        version = property("VERSION_NAME").toString()
    )

    pom {
        name.set("FlickLoad Core")
        description.set("Core caching engine with Cronet and OkHttp transports")
        configureFlickLoadPom(project)
    }
}
