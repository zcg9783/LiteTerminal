
plugins {
    id("com.android.application")
    
}

android {
    namespace = "com.zcg.liteterminal"
    compileSdk = 33
    
    defaultConfig {
        applicationId = "com.zcg.liteterminal"
        minSdk = 16
        targetSdk = 33
        versionCode = 250527
        versionName = "1.0.4"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        
   
 }
    
}


