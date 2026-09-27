# Add project specific ProGuard rules here.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Data models & Moshi
-keep class com.example.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.database.** { *; }

# Media3 / ExoPlayer & LibVLC
-keep class androidx.media3.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.example.worker.** { *; }

