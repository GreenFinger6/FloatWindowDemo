plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.floatwindowdemo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.floatwindowdemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1     // 整数，系统用来判断谁的版本更高
        versionName = "1.0" // 字符串，显示给用户看的版本名

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 关键代码：只打包指定的架构
        ndk {
            // 'arm64-v8a' 对应现代主流 64 位真机 (如：小米、华为、Vivo、OPPO、三星)
            // arm64-v8a（现代 64 位手机）、armeabi-v7a（旧款 32 位手机）、x86 和 x86_64（主要是模拟器）
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true // 启用View Binding，告别findViewById
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.transition.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 添加 Material 库（包含 TabLayout）
    implementation("com.google.android.material:material:1.12.0")
    // 添加 ViewPager2 库
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // Google ML Kit 文字识别（中文及通用模型）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    // Android 处理网络的最标准库
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 自行导入的opencv本地模型
    implementation (project(":opencv"))
}