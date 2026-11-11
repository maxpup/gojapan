plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.secrets.gradle.plugin)
}

val fixedStoreFile = providers.environmentVariable("GOJAPAN_SIGNING_STORE_FILE").orNull
val fixedStorePassword = providers.environmentVariable("GOJAPAN_SIGNING_STORE_PASSWORD").orNull
val fixedKeyAlias = providers.environmentVariable("GOJAPAN_SIGNING_KEY_ALIAS").orNull
val fixedKeyPassword = providers.environmentVariable("GOJAPAN_SIGNING_KEY_PASSWORD").orNull
val fixedSigningReady = listOf(fixedStoreFile, fixedStorePassword, fixedKeyAlias, fixedKeyPassword).all { !it.isNullOrBlank() }
if (providers.environmentVariable("GOJAPAN_REQUIRE_FIXED_SIGNING").orNull == "true" && !fixedSigningReady) {
    error("Fixed GoJapan signing credentials are required; refusing to generate a temporary signing key.")
}

android {
    namespace = "com.gojapan"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gojapan"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("goJapanFixed") {
            if (fixedSigningReady) {
                storeFile = file(checkNotNull(fixedStoreFile))
                storePassword = fixedStorePassword
                keyAlias = fixedKeyAlias
                keyPassword = fixedKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            if (fixedSigningReady) signingConfig = signingConfigs.getByName("goJapanFixed")
            buildConfigField("boolean", "ENABLE_TTS_FALLBACK", "true")
        }
        release {
            signingConfig = signingConfigs.getByName("goJapanFixed")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "ENABLE_TTS_FALLBACK", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // M1 local data and static course
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)
  androidTestImplementation(libs.androidx.room.testing)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.maps.compose)
  implementation(libs.amap.map.sdk)
}

secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
    ignoreList.add("keyToIgnore")
}
