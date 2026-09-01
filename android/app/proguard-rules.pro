# ---------------------------------------------------------------------------
# AI Master Academy — R8 / ProGuard configuration
# ---------------------------------------------------------------------------

# Keep line numbers so Crashlytics stack traces stay readable, but hide the
# original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# --- kotlinx.serialization -------------------------------------------------
# The plugin generates a synthetic $$serializer for every @Serializable class;
# reflection on it must survive shrinking.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# Our serialized DTOs and seed-content models are only ever constructed
# reflectively by the serializer, so keep their members.
-keep,includedescriptorclasses class com.aimasteracademy.app.data.network.dto.** { *; }
-keep,includedescriptorclasses class com.aimasteracademy.app.data.seed.model.** { *; }

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Retrofit / OkHttp -----------------------------------------------------
-keepattributes RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Hilt / Dagger ---------------------------------------------------------
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.hilt.** { *; }

# --- Compose ---------------------------------------------------------------
# R8 handles Compose well by default; only the tooling entry points are dropped
# deliberately, which is what we want in release.
-dontwarn androidx.compose.ui.tooling.**

# --- Firebase --------------------------------------------------------------
-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Lottie ----------------------------------------------------------------
-dontwarn com.airbnb.lottie.**

# --- Domain models used in saved-state / navigation type maps --------------
-keep class com.aimasteracademy.app.domain.model.** { *; }
-keep class com.aimasteracademy.app.navigation.** { *; }

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
