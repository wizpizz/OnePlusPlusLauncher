plugins {
    autowire(libs.plugins.android.application)
    autowire(libs.plugins.kotlin.android)
    autowire(libs.plugins.kotlin.ksp)
}

android {
    namespace = property.project.app.packageName
    compileSdk = property.project.android.compileSdk

    defaultConfig {
        applicationId = property.project.app.packageName
        minSdk = property.project.android.minSdk
        targetSdk = property.project.android.targetSdk
        versionName = property.project.app.versionName
        versionCode = property.project.app.versionCode
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPPORTED_LAUNCHER_VERSION", "\"15.8.17\"")
    }

    signingConfigs {
        // Read from environment first; if missing, read from a local .env file (ignored by Git)
        val env = System.getenv()
        val dotEnvFile = rootProject.file(".env")
        val dotEnv: Map<String, String> = if (dotEnvFile.exists()) {
            dotEnvFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    var value = line.substring(idx + 1).trim()
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                        value = value.substring(1, value.length - 1)
                    }
                    key to value
                }
        } else emptyMap()

        fun readSecret(name: String): String? = env[name] ?: dotEnv[name]

        val ksPath = readSecret("SIGNING_KEY_STORE_PATH")
        val ksAlias = readSecret("SIGNING_KEY_ALIAS")
        val ksStorePassword = readSecret("SIGNING_KEY_STORE_PASSWORD")
        val ksKeyPassword = readSecret("SIGNING_KEY_PASSWORD")

        if (!ksPath.isNullOrEmpty() && !ksAlias.isNullOrEmpty() && !ksStorePassword.isNullOrEmpty() && !ksKeyPassword.isNullOrEmpty()) {
            create("release") {
                storeFile = (ksPath as Any?)?.let { file(it) }
                storePassword = ksStorePassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the signing configuration
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    lint { checkReleaseBuilds = false }
    // TODO Please visit https://highcapable.github.io/YukiHookAPI/en/api/special-features/host-inject
    // TODO 请参考 https://highcapable.github.io/YukiHookAPI/zh-cn/api/special-features/host-inject
    // androidResources.additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x64")
}

dependencies {
    compileOnly(de.robv.android.xposed.api)
    implementation(com.highcapable.yukihookapi.api)
    ksp(com.highcapable.yukihookapi.ksp.xposed)
    implementation(com.github.duanhong169.drawabletoolbox)
    implementation(androidx.core.core.ktx)
    implementation(androidx.appcompat.appcompat)
    implementation(com.google.android.material.material)
    implementation(androidx.constraintlayout.constraintlayout)
    testImplementation(junit.junit)
    androidTestImplementation(androidx.test.ext.junit)
    androidTestImplementation(androidx.test.espresso.espresso.core)
    implementation(me.xdrop.fuzzywuzzy)
}