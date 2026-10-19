plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.daklok.crescendo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daklok.crescendo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // zmenšenie a optimalizácia kódu (R8) + odstránenie nepoužitých zdrojov
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // „staging“ = rovnaké ako release (R8), ale podpísané ladiacim kľúčom – na vyskúšanie
        // release verzie priamo z Android Studia (Build Variants → staging), prepíše debug verziu
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-staging"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // vzorky klavíra (OGG) sa čítajú priamo z APK – nesmú byť skomprimované
    androidResources {
        noCompress += "ogg"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG – v ladiacej verzii sa dá Pro zapnúť bez nákupu (na skúšanie)
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.face.detection)
    // skenovanie nôt fotoaparátom (orezanie, vyrovnanie, PDF)
    implementation(libs.mlkit.document.scanner)
    // rozpoznanie názvu a skladateľa z prvej strany pri importe (model cez Google Play služby)
    implementation(libs.mlkit.text.recognition)
    // spájanie PDF (doplnenie strán do existujúcich nôt) bez straty kvality
    implementation(libs.pdfbox.android)

    // Záloha na Google Drive
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)

    // Crescendo Pro (nákup / predplatné cez Google Play)
    implementation(libs.billing.ktx)
}
