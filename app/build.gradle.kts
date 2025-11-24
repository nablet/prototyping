plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

android {
	namespace = "com.cloudstaff.myapplication"
	compileSdk {
		version = release(36)
	}

	defaultConfig {
		applicationId = "com.cloudstaff.myapplication"
		minSdk = 24
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

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
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	kotlinOptions {
		jvmTarget = "11"
	}
	buildFeatures {
		compose = true
		viewBinding = true
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.play.services.location)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)

	implementation("androidx.appcompat:appcompat:1.7.1")
	implementation("androidx.constraintlayout:constraintlayout:2.2.1")
	implementation("androidx.activity:activity-ktx:1.12.0")
	implementation("androidx.appcompat:appcompat:1.7.1")
	implementation("androidx.recyclerview:recyclerview:1.4.0")
	implementation("androidx.cardview:cardview:1.0.0")
	implementation("com.squareup.retrofit2:retrofit:2.9.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
	implementation("com.google.android.gms:play-services-maps:18.2.0")
	implementation("com.google.code.gson:gson:2.10.1")


	implementation("com.squareup.retrofit2:retrofit:2.9.0")
	implementation("com.squareup.retrofit2:converter-gson:2.9.0")
// JSON parsing
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// coroutines
	implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.11")
// optional

}