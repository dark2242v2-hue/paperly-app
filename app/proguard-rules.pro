# Paperly Release R8 / ProGuard Configuration
# Obfuscates app classes while preserving Google Play Billing, AdMob, JSON/Gson models,
# Coroutines, and PDFBox Android dependencies.

# Keep line numbers for crash stack traces while hiding original source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations, generics, and enclosing method metadata needed by libraries
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# Google Play Billing Library
# ------------------------------------------------------------------------------
-keep class com.android.billingclient.** { *; }
-keep class com.google.android.gms.internal.play_billing.** { *; }
-dontwarn com.android.billingclient.**
-dontwarn com.google.android.gms.internal.play_billing.**

# ------------------------------------------------------------------------------
# Google Mobile Ads (AdMob) SDK
# ------------------------------------------------------------------------------
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-dontwarn com.google.android.gms.ads.**

# ------------------------------------------------------------------------------
# Gson / JSON Serialization Models
# ------------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}
-dontwarn com.google.gson.**

# Kotlinx Serialization / DataStore / Room generated classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**

# ------------------------------------------------------------------------------
# Kotlin Coroutines
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# PDFBox Android (suppress optional desktop Java AWT/XML references)
# ------------------------------------------------------------------------------
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.fontbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
