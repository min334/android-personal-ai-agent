# ─────────────────────────────────────────────────────────────────────────────
#  ProGuard / R8 Rules — Personal AI Agent
# ─────────────────────────────────────────────────────────────────────────────
#
#  These rules prevent R8 from shrinking/obfuscating classes that are
#  instantiated reflectively (Gson, Retrofit, Hilt, etc.).
#
#  TIP: Check the build/outputs/mapping/ folder after a release build to
#  review what was kept and what was removed.
# ─────────────────────────────────────────────────────────────────────────────

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Gemini API data classes (serialized/deserialized by Gson) ─────────────────
# These must NOT be obfuscated because Gson matches JSON field names to field names.
-keep class com.minthitsaraung.personalaiagent.data.model.** { *; }

# ── Retrofit / OkHttp ────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# ── Gson ─────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * {
    @javax.inject.Inject <fields>;
}
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Android security / EncryptedSharedPreferences ────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Accessibility Service ─────────────────────────────────────────────────────
-keep class com.minthitsaraung.personalaiagent.accessibility.** { *; }
-keep class com.minthitsaraung.personalaiagent.overlay.** { *; }
-keep class com.minthitsaraung.personalaiagent.service.** { *; }

# ── BuildConfig ───────────────────────────────────────────────────────────────
-keep class com.minthitsaraung.personalaiagent.BuildConfig { *; }
