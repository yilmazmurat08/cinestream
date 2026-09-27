# Add project specific ProGuard rules here.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Data models & Moshi
-keep class com.example.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
# Retrofit'in suspend fonksiyonlarında dönüş tipi silindiği için (Continuation<T>) R8, yalnızca JSON'dan
# oluşturulan model sınıflarını kaldırıyordu (ör. MetadataEnricher$TMDBResponse, $TMDBPersonResult).
# @JsonClass modelleri ve codegen adapter'ları her paket için korunur.
-keep @com.squareup.moshi.JsonClass class com.example.** { *; }
-keep class com.example.**JsonAdapter { *; }
# KotlinJsonAdapterFactory (yansıma) Kotlin metadata'sına ihtiyaç duyar.
-keep class kotlin.Metadata { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Room Database (Room'un kendi consumer kuralları DAO/_Impl sınıflarını korur)
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.db.** { *; }

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


# ZXing (TV'de "Telefonla gir" QR kodu): yansıma kullanmaz, ek kural gerekmez.
# Firebase AI / App Check ve Play Review kendi consumer kurallarını getirir.

# Release: ayrıntılı Log.d / Log.v çağrıları derlemeden çıkarılır (Log.i/w/e kalır).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
