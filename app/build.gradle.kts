plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dash.btmanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.dash.btmanager"
        // ГУ работает на Android 9 (API 28). targetSdk 28 — без разрешений
        // BLUETOOTH_SCAN/CONNECT из Android 12; приложение только для
        // сайдлоада, требований Play нет.
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    // full — все вкладки (Bluetooth, OBD, Данные, Экран);
    // lite — только Bluetooth: поиск и спаривание устройств + журнал,
    // идёт отдельным пакетом com.dash.btmanager.lite.
    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "LITE", "false")
        }
        create("lite") {
            dimension = "version"
            applicationIdSuffix = ".lite"
            versionNameSuffix = " (Lite)"
            resValue("string", "app_name", "Bluetooth Manager Lite")
            buildConfigField("boolean", "LITE", "true")
        }
    }

    // Подпись debug-ключом (лежит в репе): локальная и будущие CI-сборки
    // дают одинаковую подпись и ставятся поверх друг друга.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 режет мёртвый код: в lite-варианте выкидывает весь obd-движок
            // (в MainActivity он за константой BuildConfig.LITE), а в full
            // убирает неиспользуемые классы. Отражения только по классам
            // платформы — см. proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

// Имя APK: bt_manager-<buildType>.apk вместо app-….apk.
base {
    archivesName = "bt_manager"
}

dependencies {
    testImplementation(libs.junit)
    // org.json в android.jar — заглушка; для тестов разбора профилей нужна настоящая.
    testImplementation(libs.json)
}
