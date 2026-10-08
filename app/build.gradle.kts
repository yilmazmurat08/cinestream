plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.cinestream.iptv"
    minSdk = 26
    targetSdk = 36
    versionCode = 8
    versionName = "1.0.7"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // 60 dakikalık ücretsiz izleme sınırı: mağaza (release) sürümünde açık, test sürümlerinde kapalı.
    buildConfigField("boolean", "FREE_WATCH_LIMIT", "true")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      // Uses default Android SDK debug keystore automatically
      buildConfigField("boolean", "FREE_WATCH_LIMIT", "false")
    }
    // Test sürümü: release ile aynı (küçültülmüş, hızlı) ama debug anahtarıyla imzalı ve izleme sınırı yok.
    // Derleme: ./gradlew assembleQa  → app/build/outputs/apk/qa/app-qa.apk
    create("qa") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      buildConfigField("boolean", "FREE_WATCH_LIMIT", "false")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Stres testleri (com.example.stress) yalnızca istenince çalışır:
      // ./gradlew testDebugUnitTest -Pstress=true --tests 'com.example.stress.*'
      all {
        it.systemProperty("cinestream.stress", project.findProperty("stress")?.toString() ?: "false")
        // Birden çok Android sürümünü (TV testleri) yükleyen Robolectric testleri için yeterli bellek.
        it.maxHeapSize = "3g"
      }
    }
  }
  // MigrationTestHelper şema JSON'larını test assets'inden okur.
  sourceSets {
    // Robolectric unit testleri uygulamanın (debug) assets'ini okur; release APK etkilenmez.
    getByName("debug").assets.directories.add("$projectDir/schemas")
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
  }
  lint {
    // Lint hataları (ör. NewApi, MissingPermission) derlemeyi durdurur; release derlemesinde de kontrol edilir.
    abortOnError = true
    checkReleaseBuilds = true
  }
  // Uygulama içinden TR/EN dil değiştirilebildiği için tüm dil kaynakları her kurulumda bulunmalı
  // (Play, AAB dil bölmesiyle yalnızca cihaz dilini indirirdi; lint AppBundleLocaleChanges).
  bundle {
    language {
      enableSplit = false
    }
  }
}

// Automatically generate .env file from environment variables if present without committing secrets.
// Gemini anahtarı uygulamaya gömülmez: yapay zekâ yalnızca kullanıcının kendi girdiği anahtarla çalışır.
val envFile = rootProject.file(".env")
val tmdbApiKey = System.getenv("TMDB_API_KEY") ?: ""
val youtubeApiKey = System.getenv("YOUTUBE_API_KEY") ?: ""
val finalTmdb = tmdbApiKey.ifEmpty { "placeholder" }
val finalYoutube = youtubeApiKey.ifEmpty { "placeholder" }
if (!envFile.exists() || envFile.readText().contains("GEMINI_API_KEY") || tmdbApiKey.isNotEmpty() || youtubeApiKey.isNotEmpty()) {
  envFile.writeText("TMDB_API_KEY=\"$finalTmdb\"\nYOUTUBE_API_KEY=\"$finalYoutube\"\n")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Room şema dosyaları (app/schemas/) sürüm kontrolünde tutulur; migration testleri bunları kullanır.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}



// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // Android TV arayüzü (TV modu) için Compose for TV bileşenleri.
  implementation(libs.androidx.tv.material)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material3:material3-window-size-class")
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // QR kod (TV'de "Telefonla gir")
  implementation("com.google.zxing:core:3.5.3")
  implementation(libs.converter.moshi)
  implementation(libs.billing.ktx)
  implementation(libs.play.review)
  implementation(libs.play.review.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
