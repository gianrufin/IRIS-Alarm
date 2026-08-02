# ML Kit ships model metadata that is looked up reflectively.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Room entities are constructed reflectively by generated code.
-keep class com.iris.alarm.data.local.** { *; }
