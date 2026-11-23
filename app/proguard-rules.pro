-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.openjarvis.android.ai.** { *; }
-keepclassmembers class com.openjarvis.android.data.** { *; }
