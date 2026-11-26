import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    id("kotlin-parcelize")
}

fun findSigningKeyPair(dir: File): Pair<File, File>? {
    val pk8Files = dir.listFiles { file ->
        file.isFile && file.name.endsWith(".pk8", ignoreCase = true)
    }?.sortedBy { it.name } ?: return null
    val certFiles = dir.listFiles { file ->
        file.isFile && file.name.endsWith(".pem", ignoreCase = true)
    }?.sortedBy { it.name } ?: return null
    if (pk8Files.isEmpty() || certFiles.isEmpty()) return null

    // Prefer exact stem match (e.g. foo.pk8 + foo.x509.pem or foo.pk8 + foo.pem)
    for (pk8 in pk8Files) {
        val stem = pk8.name.substringBeforeLast('.')
        val matchingCert = certFiles.firstOrNull {
            it.name.equals("$stem.x509.pem", ignoreCase = true)
        } ?: certFiles.firstOrNull {
            it.name.equals("$stem.pem", ignoreCase = true)
        }
        if (matchingCert != null) return pk8 to matchingCert
    }
    return if (pk8Files.size == 1 && certFiles.size == 1) {
        pk8Files.first() to certFiles.first()
    } else {
        null
    }
}

val signingKeyPair = findSigningKeyPair(rootProject.layout.projectDirectory.asFile)
    ?: findSigningKeyPair(layout.projectDirectory.asFile)
val platformPk8File = signingKeyPair?.first
val platformCertFile = signingKeyPair?.second
val hasPlatformKeys = platformPk8File != null && platformCertFile != null
val platformKeystoreFile = layout.buildDirectory.file("generated/signing/platform.p12")

android {
    namespace = "com.android.multimediastudio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.android.multimediastudio"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasPlatformKeys) {
        signingConfigs {
            create("platform") {
                storeFile = platformKeystoreFile.get().asFile
                storeType = "PKCS12"
                storePassword = "android"
                keyAlias = "platform"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            if (hasPlatformKeys) {
                signingConfig = signingConfigs.getByName("platform")
            }
        }
        release {
            if (hasPlatformKeys) {
                signingConfig = signingConfigs.getByName("platform")
            }
            optimization {
                enable = false
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.21.0+"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive.core)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

if (platformPk8File != null && platformCertFile != null) {
    val generatePlatformKeystore = tasks.register("generatePlatformKeystore") {
        val pk8Input = platformPk8File
        val certInput = platformCertFile
        val keystoreOutput = platformKeystoreFile

        inputs.file(pk8Input)
        inputs.file(certInput)
        outputs.file(keystoreOutput)

        doLast {
            val certFactory = CertificateFactory.getInstance("X.509")
            val certs = certInput.inputStream().use {
                certFactory.generateCertificates(it)
            }.toTypedArray()
            require(certs.isNotEmpty()) { "No certificates found in ${certInput.absolutePath}" }

            val rawKeyBytes = pk8Input.readBytes()
            val keyText = String(rawKeyBytes, Charsets.US_ASCII)
            val keyBytes = if (keyText.contains("BEGIN PRIVATE KEY")) {
                val base64Body = keyText
                    .substringAfter("-----BEGIN PRIVATE KEY-----")
                    .substringBefore("-----END PRIVATE KEY-----")
                    .replace(Regex("\\s+"), "")
                Base64.getDecoder().decode(base64Body)
            } else {
                rawKeyBytes
            }

            val algorithm = certs.first().publicKey.algorithm
            val privateKey = KeyFactory.getInstance(algorithm)
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))

            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("platform", privateKey, "android".toCharArray(), certs)
            }
            val outFile = keystoreOutput.get().asFile
            outFile.parentFile.mkdirs()
            outFile.outputStream().use { keyStore.store(it, "android".toCharArray()) }
        }
    }

    tasks.configureEach {
        if (name.startsWith("validateSigning") || name.startsWith("package") || name.startsWith("sign")) {
            dependsOn(generatePlatformKeystore)
        }
    }
}