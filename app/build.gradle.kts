import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)

    id("kotlin-parcelize")

    alias(libs.plugins.dependency.analysis) apply true
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

android {
    namespace = "slowscript.warpinator"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "slowscript.warpinator"
        minSdk = 23 // Required by compose
        targetSdk =  36
        versionCode = 1090
        versionName = "1.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    androidResources {
        generateLocaleConfig =  true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // State management
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation("org.openjax.security:nacl:0.3.2") //Update available, but API is weird now
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.82")
    implementation("io.grpc:grpc-netty:1.75.0")
    implementation("io.grpc:grpc-okhttp:1.75.0")
    implementation("io.grpc:grpc-protobuf:1.75.0") {
        exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
    }
    implementation("io.grpc:grpc-stub:1.75.0")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.8")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    implementation("com.github.tony19:logback-android:3.0.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.guava:guava:33.4.8-android")
    //This was included by gRPC anyway, so why not use it
    implementation("org.jmdns:jmdns:3.5.8")
    //Device discovery worsened after update, let's see if this was the problem
    // Also, new versions require desugaring on Android 5 and 6
    implementation("org.slf4j:slf4j-api:2.0.17") // For jmdns, it declares a too old dependency
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.8"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.75.0"
        }

        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.5.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
            task.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}

//If there is a better way to get rid of Netty logging, let me know
configurations.configureEach {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("ch.qos.logback:logback-classic")).using(module("com.github.tony19:logback-android:3.0.0"))
        }
    }
}
