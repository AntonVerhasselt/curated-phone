plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp) }
fun publicValue(name: String, fallback: String = "") = providers.gradleProperty(name).orElse(providers.environmentVariable(name)).getOrElse(fallback)
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
android {
    namespace = "be.anton.curatedphone"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "be.anton.curatedphone"; minSdk = 30; targetSdk = 36
        versionCode = 1; versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["testOnly"] = "false"
        buildConfigField("String", "BACKEND_URL", quoted(publicValue("CURATED_BACKEND_URL", "https://tidy-jaguar-244.eu-west-1.convex.site")))
        buildConfigField("String", "APPROVAL_KEY_ID", quoted(publicValue("CURATED_APPROVAL_KEY_ID", "development-p256-1")))
        val keyFile = publicValue("CURATED_APPROVAL_PUBLIC_KEY_FILE")
        val key = if (keyFile.isNotEmpty()) file(keyFile).readText() else publicValue("CURATED_APPROVAL_PUBLIC_KEY")
        buildConfigField("String", "APPROVAL_PUBLIC_KEY", quoted(key))
        buildConfigField("boolean", "SAMSUNG_REMOVAL_VALIDATED", publicValue("CURATED_REMOVAL_VALIDATED", "false"))
        buildConfigField("boolean", "BYPASS_PROTECTION_VALIDATED", publicValue("CURATED_BYPASS_PROTECTION_VALIDATED", "false"))
    }
    signingConfigs {
        create("privateRelease") {
            val path = providers.environmentVariable("CURATED_KEYSTORE_FILE").orNull
            if (path != null) { storeFile = file(path); storePassword = System.getenv("CURATED_KEYSTORE_PASSWORD"); keyAlias = System.getenv("CURATED_KEY_ALIAS"); keyPassword = System.getenv("CURATED_KEY_PASSWORD") }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = null; manifestPlaceholders["testOnly"] = "true" }
        release { isMinifyEnabled = true; isShrinkResources = true; signingConfig = if (providers.environmentVariable("CURATED_KEYSTORE_FILE").isPresent) signingConfigs.getByName("privateRelease") else null; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material); implementation(libs.compose.preview)
    implementation(libs.activity.compose); implementation(libs.lifecycle.viewmodel); implementation(libs.lifecycle.runtime)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.work.runtime); implementation(libs.serialization.json); implementation(libs.okhttp)
    // Obtain this proprietary SDK from Samsung. It is never redistributed in this repository.
    implementation(fileTree("libs") { include("knoxsdk.jar") })
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.android.test); androidTestImplementation(libs.test.runner)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.test)
}
