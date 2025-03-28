plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
}

android {
    namespace = "com.pedro.streamer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pedro.streamer"
        minSdk = 16
        targetSdk = 35
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    //
    sourceSets.all {
        //设置目标的so存放路径
        jniLibs.srcDirs("src/main/jniLibs")
    }
}

dependencies {
    implementation(project(":library"))
    implementation(project(":extra-sources"))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.core)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
