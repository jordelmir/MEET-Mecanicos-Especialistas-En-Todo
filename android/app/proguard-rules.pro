# Add project specific ProGuard rules here.
-keep class com.elysium369.meet.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn okio.**
-keep class com.github.mikephil.charting.** { *; }
-dontwarn org.slf4j.impl.StaticLoggerBinder

# WorkManager instantiates InputMerger implementations by their persisted class
# name. Release shrinking must retain the public no-arg constructor or queued
# work cannot start after installation/process recreation.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}

# HiltWorker uses @AssistedInject constructors; keep them for WorkManager
-keep class dagger.hilt.work.HiltWorker
-keepclassmembers class * extends androidx.work.Worker {
    <init>(...);
}
-keep class com.elysium369.meet.ride.work.** { *; }

# Ktor's optional IntelliJ debugger detector probes JVM-only management APIs.
# Android has no java.lang.management package and the detector safely falls
# back when those classes are absent; suppress only these two precise R8 edges.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
