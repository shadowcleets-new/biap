# --- kotlinx-serialization: keep generated serializers for our models ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.sc.biap.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sc.biap.**$$serializer { *; }
-keepclassmembers class com.sc.biap.** {
    *** Companion;
}

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Google Sign-In reflection surface ---
-keep class com.google.android.gms.auth.** { *; }

# Room 2.8.4, Coil 3, WorkManager and play-services ship consumer rules; no extra rules here.
