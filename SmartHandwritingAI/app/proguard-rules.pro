# ============================================================
# SmartHandwritingAI — ProGuard Rules
# ============================================================

# ── Room Database ────────────────────────────────────────────
-keep class dat.nguyenvan.smarthandwritingai.PredictionEntity { *; }
-keep class dat.nguyenvan.smarthandwritingai.CharacterStat { *; }
-keep interface dat.nguyenvan.smarthandwritingai.PredictionDao { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ── TensorFlow Lite ──────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# ── Lottie ───────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── UCrop ────────────────────────────────────────────────────
-keep class com.yalantis.ucrop.** { *; }
-keepclassmembers class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# ── MPAndroidChart ───────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── Firebase ─────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# Firebase Auth specific
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.auth.internal.** { *; }

# ── Material / AndroidX ──────────────────────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.**

# ── App Activities / Services ────────────────────────────────
-keep public class dat.nguyenvan.smarthandwritingai.** { *; }

# ── TextToSpeech ─────────────────────────────────────────────
-keep class android.speech.tts.** { *; }

# ── General Android ──────────────────────────────────────────
# Preserve Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
# Preserve enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Preserve Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── ViewBinding / R class ───────────────────────────────────
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# ── Remove logging in release ────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}