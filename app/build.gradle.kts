plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val dotEnvValues: Map<String, String> = run {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) {
        emptyMap()
    } else {
        envFile.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .map { line ->
                val idx = line.indexOf('=')
                val key = line.substring(0, idx).trim()
                val rawValue = line.substring(idx + 1).trim()
                val value = rawValue.removeSurrounding("\"").removeSurrounding("'")
                key to value
            }
            .toMap()
    }
}

fun resolveConfigValue(key: String, default: String = ""): String {
    return dotEnvValues[key]
        ?: (findProperty(key) as String?)
        ?: System.getenv(key)
        ?: default
}

val geminiApiKey: String = resolveConfigValue("JARVIS_GEMINI_API_KEY")

val llmBackend: String = resolveConfigValue("JARVIS_LLM_BACKEND", "hybrid")

val liteRtModelDir: String = resolveConfigValue("JARVIS_LITERT_MODEL_DIR")

val huggingFaceToken: String = resolveConfigValue("JARVIS_HF_TOKEN")
val remoteAgentBaseUrl: String = resolveConfigValue("JARVIS_REMOTE_AGENT_BASE_URL")

fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.jarvis.app"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    defaultConfig {
        applicationId = "com.jarvis.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "JARVIS_GEMINI_API_KEY", "\"${esc(geminiApiKey)}\"")
        buildConfigField("String", "JARVIS_LLM_BACKEND", "\"${esc(llmBackend)}\"")
        buildConfigField("String", "JARVIS_LITERT_MODEL_DIR", "\"${esc(liteRtModelDir)}\"")
        buildConfigField("String", "JARVIS_HF_TOKEN", "\"${esc(huggingFaceToken)}\"")
        buildConfigField("String", "JARVIS_REMOTE_AGENT_BASE_URL", "\"${esc(remoteAgentBaseUrl)}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Updated to latest version which supports .litertlm format used by Gemma 3/4
    implementation("com.google.mediapipe:tasks-genai:0.10.33")

    implementation(project(":core"))
    implementation(project(":input"))
    implementation(project(":intent"))
    implementation(project(":planner"))
    implementation(project(":execution"))
    implementation(project(":skills"))
    implementation(project(":memory"))
    implementation(project(":llm"))
    implementation(project(":logging"))
    implementation(project(":output"))

    testImplementation(kotlin("test"))

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
