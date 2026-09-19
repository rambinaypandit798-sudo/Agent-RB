-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class com.rbagent.assistant.data.** { *; }
-keep class com.rbagent.assistant.ai.** { *; }
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
-keep class android.speech.** { *; }
-keep class android.speech.tts.** { *; }
-keep class com.rbagent.assistant.voice.ForegroundVoiceService { *; }
-keep class com.rbagent.assistant.service.AccessibilityHelperService { *; }
-keep class com.rbagent.assistant.receiver.** { *; }
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
