# WebRTC exposes and reaches implementations through JNI, factories, and reflection.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Bouncy Castle's certificate classes are called directly. Keep missing optional-platform
# references quiet while allowing R8 to remove unused algorithms and implementation classes.
-dontwarn org.bouncycastle.**
