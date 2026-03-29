# Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep model classes (JSON serialization)
-keep class com.bossmanager.mobile.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Play Core (missing classes used by Flutter deferred components — not used in this app)
-dontwarn com.google.android.play.core.**
