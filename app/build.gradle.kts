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
// 私钥绝不进仓库。四项信息缺一不可；**release 变体不许回退 debug 签名** ——
// debug keystore 每台机器都不一样，回退一次就会签出一个谁也覆盖不上的包。
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (signingProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")?.let { file(it) }
val releaseStorePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// CI 用 -PrequireReleaseSigning=true 调起，缺密钥在配置阶段就报错，连构建都不开始。
if (providers.gradleProperty("requireReleaseSigning").orNull == "true" && !hasReleaseSigning) {
    throw GradleException(
        "release 构建要求完整的签名配置：提供 keystore.properties 或 " +
            "SIGNING_STORE_FILE / SIGNING_STORE_PASSWORD / SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD",
    )
}

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
        // 拿到完整密钥信息才创建；没有就留空 —— 下面 release 变体不会拿 debug key 顶上
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 故意不给 debug 回退：没密钥就产出 unsigned 包，并在打包任务里直接失败
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
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

// 最后一道闸：不管谁用什么方式调 release 任务，没密钥就在出包前停下（debug 变体不受影响）
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "拒绝出 release 包：缺少签名密钥库。请提供 keystore.properties 或 SIGNING_* 环境变量，" +
                    "详见 docs/SECURITY.md。用 debug keystore 签出来的包装不到用户手机上。",
            )
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
