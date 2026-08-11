# Add project specific ProGuard rules here.
-keep class com.elysium369.meet.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn okio.**
-keep class com.github.mikephil.charting.** { *; }
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Ktor's optional IntelliJ debugger detector probes JVM-only management APIs.
# Android has no java.lang.management package and the detector safely falls
# back when those classes are absent; suppress only these two precise R8 edges.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
