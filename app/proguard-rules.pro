# WebRTC exposes and reaches implementations through JNI, factories, and reflection.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# TLS identity and provider classes are loaded through provider registration and reflection.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# The playback-capture bridge intentionally accesses the pinned WebRTC ADM internals.
-keep class org.webrtc.audio.** { *; }

# Keep app service entry points and callback implementations discoverable after shrinking.
-keep class com.carcast.mirror.service.** { *; }
-keep class com.carcast.mirror.core.** { *; }
