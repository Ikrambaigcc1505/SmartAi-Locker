# Preserve source file and line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep our app's Application, Receivers, Services, Activities, Disguise Aliases, and Ad Manager
-keep class com.example.SmartAiApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.viewmodel.** { *; }
-keep class com.example.security.** { *; }
-keep class com.example.ads.** { *; }
-dontwarn androidx.datastore.**
-dontwarn java.lang.invoke.StringConcatFactory

# Kotlin Serialization (VaultRoute, SmartVaultBackupManifest, SmartVaultBackupPackage)
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.data.**$$serializer { *; }
-keepclassmembers class com.example.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Jetpack Compose & Lifecycle
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-dontwarn androidx.compose.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepclassmembers class * implements androidx.media3.common.Player$Listener { *; }

# CameraX & Guava ListenableFuture
-keep class androidx.camera.** { *; }
-keepclassmembers class androidx.camera.** { *; }
-dontwarn com.google.common.util.concurrent.ListenableFuture

# AndroidX Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Google Mobile Ads (AdMob) & UMP Consent SDK (Banner, Interstitial, Native Advanced)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.ads.nativead.** { *; }
-keep class com.google.android.ump.** { *; }
-keep public class * extends com.google.android.gms.ads.AdListener
-keep public class * extends com.google.android.gms.ads.FullScreenContentCallback
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**

# JCE / AES-GCM / PBKDF2 Crypto classes
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
