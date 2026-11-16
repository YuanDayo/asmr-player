import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionName = "2.5.2-beta1"
val appVersionCode = 54

// 签名：本地读仓库根目录 keystore.properties（已在 .gitignore 里），CI 读环境变量。
// 私钥绝不进仓库。取不到时回退 debug 签名，保证本地 clone 下来就能跑。
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (signingProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")?.let { file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true

android {
    namespace = "com.kiite.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kiite.player"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 只在拿到密钥库时才创建；CI 缺 secret 会由 workflow 里的守卫步骤直接失败，
        // 不会静默产出另一个签名（曾经因为 CI 用临时 debug key 签包，导致装不上）
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (hasReleaseSigning) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        // 应用内「关于」等位置读 BuildConfig.VERSION_NAME，别再手写版本号
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions { unitTests.isReturnDefaultValues = true }

    // 产物文件名改成「包名 + 版本号」，例如 com.kiite.player-1.2.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "com.kiite.player-" + appVersionName + ".apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
