-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# hCaptcha is launched through a FragmentActivity wrapper and internally uses reflection/WebView bridge code.
-keep class com.hcaptcha.sdk.** { *; }
-dontwarn com.hcaptcha.sdk.**

# Kotlin serialization generates serializers at compile time, but keeping annotations helps DTO decoding survive shrinker optimizations.
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# OkHttp/Cronet may reference optional platform integrations depending on device image.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.chromium.net.**
