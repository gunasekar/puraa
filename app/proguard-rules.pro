# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.puraa.**$$serializer { *; }
-keepclassmembers class com.puraa.** {
    *** Companion;
}
-keepclasseswithmembers class com.puraa.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp — already optimized; suppress warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager instantiates the worker by reflection; keep its constructor.
-keep class com.puraa.relay.RelayWorker { <init>(...); }

# Tink (via androidx.security.crypto / EncryptedSharedPreferences) references
# compile-only errorprone annotations that aren't packaged. Harmless to strip.
-dontwarn com.google.errorprone.annotations.**

# Strip debug logging from release so sender ids / API error bodies never
# reach logcat in production. (Log.e is kept for crash diagnosis.)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
