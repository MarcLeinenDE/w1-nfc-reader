plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasStableSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val buildCommit = providers.environmentVariable("GITHUB_SHA").orNull
    ?.trim()
    ?.take(12)
    ?.ifEmpty { null }
    ?: "local"
val sourceRepositoryUrl = providers.environmentVariable("QALCOSONIC_SOURCE_REPOSITORY_URL").orNull
    ?.trim()
    .orEmpty()

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    // Keep the established Java namespace to avoid a large mechanical package refactor. The
    // public Android app identity is the applicationId below and remains stable across v2.
    namespace = "de.marcleinen.engineeringlab.qalcosonic"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.marcleinen.w1nfcreader"
        minSdk = 24
        targetSdk = 35
        versionCode = 39
        versionName = "2.0.0-dev"

        buildConfigField("String", "BUILD_COMMIT", buildConfigString(buildCommit))
        buildConfigField("String", "SOURCE_REPOSITORY_URL", buildConfigString(sourceRepositoryUrl))
        buildConfigField("boolean", "STABLE_SIGNING", hasStableSigning.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Local contributors can use Android's normal debug key. CI may inject a stable
            // development/release identity explicitly through environment variables.
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
