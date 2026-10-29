import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.nav.safeargs)
}

val pattern: DateTimeFormatter? = DateTimeFormatter.ofPattern("yyMMdd_HHmm")
val now: String? = LocalDateTime.now().format(pattern)

// 签名信息从根目录 local.properties 读取（不入库），示例：
// tt.storePassword=xxx
// tt.keyAlias=ttkey
// tt.keyPassword=xxx
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

configure<ApplicationExtension> {
    namespace = "top.bogey.touch_tool"
    compileSdk = common.versions.targetSdk.get().toInt()
    ndkVersion = common.versions.ndkVersion.get()
    buildToolsVersion = common.versions.buildToolsVersion.get()

    defaultConfig {
        applicationId = "top.bogey.touch_tool"
        minSdk = common.versions.minSdk.get().toInt()
        targetSdk = common.versions.targetSdk.get().toInt()
        // 版本号自动化：versionCode 按构建日期（yyyyMMdd）递增，versionName 为构建时间
        versionCode = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
        versionName = now

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wno-format")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("localRelease") {
            val ks = rootProject.file("tt-release.keystore")
            val sp = localProps.getProperty("tt.storePassword", "")
            val kp = localProps.getProperty("tt.keyPassword", "")
            if (ks.exists() && sp.isNotEmpty() && kp.isNotEmpty()) {
                storeFile = ks
                storePassword = sp
                keyAlias = localProps.getProperty("tt.keyAlias", "ttkey")
                keyPassword = kp
            }
        }
    }

    buildTypes {

        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "点击助手Debug")
        }

        release {
            isMinifyEnabled = false
            resValue("string", "app_name", "点击助手")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file("tt-release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("localRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = common.versions.cmakeVersion.get()
        }
    }

    buildFeatures {
        viewBinding = true
        aidl = true
        resValues = true
    }
}

tasks.withType<PackageAndroidArtifact>().configureEach {
    if (name.contains("release", true)) {
        doLast {
            val dir = outputDirectory.get().asFile
            val apk = dir.listFiles()?.firstOrNull { it.extension == "apk" } ?: return@doLast

            val target = File(dir, "点击助手_${now}.APK")
            apk.copyTo(target, true)
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)

    implementation(libs.nav.fragment)
    implementation(libs.nav.ui)

    implementation(libs.flexbox)

    implementation(libs.mmkv)
    implementation(libs.gson)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.exp4j)
    implementation(libs.zxinglite)
    implementation(libs.tinypinyin)
    implementation(libs.hiddenapibypass)

    implementation("org.mozilla:rhino:1.7.14")

    implementation(libs.litert)
}