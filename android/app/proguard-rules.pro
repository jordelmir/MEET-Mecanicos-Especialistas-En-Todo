# ══════════════════════════════════════════════════════════════════════════════
# MEET / Elysium Vanguard — ProGuard / R8 rules (Play Store release)
# ══════════════════════════════════════════════════════════════════════════════

# ── Hilt / Dagger ────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Kotlinx Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.elysium369.meet.** {
    *** Companion;
}
-keepclasseswithmembers class com.elysium369.meet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Supabase SDK ─────────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.github.jan.supabase.postgrest.** { *; }
-keep class io.github.jan.supabase.gotrue.** { *; }
-keep class io.github.jan.supabase.realtime.** { *; }

# ── Room entities & DAOs ────────────────────────────────────────────────────
-keep class com.elysium369.meet.data.local.entities.** { *; }
-keep class com.elysium369.meet.data.local.dao.** { *; }

# ── WorkManager ──────────────────────────────────────────────────────────────
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
-keep class dagger.hilt.work.HiltWorker
-keepclassmembers class * extends androidx.work.Worker {
    <init>(...);
}
-keep class com.elysium369.meet.ride.work.** { *; }

# ── Charts (MPAndroidChart) ──────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── Ktor ─────────────────────────────────────────────────────────────────────
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# ── Okio ─────────────────────────────────────────────────────────────────────
-dontwarn okio.**
-keep class okio.** { *; }

# ── Supabase Realtime channels (reflection) ─────────────────────────────────
-keep class io.github.jan.supabase.realtime.** { *; }

# ── LiveKit ──────────────────────────────────────────────────────────────────
-keep class io.livekit.android.** { *; }

# ── MapLibre ─────────────────────────────────────────────────────────────────
-keep class com.mapbox.** { *; }
-keep class org.maplibre.** { *; }

# ── SceneView / Filament ─────────────────────────────────────────────────────
-keep class io.github.sceneview.** { *; }

# ── MLKit ────────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Google Play Billing ──────────────────────────────────────────────────────
-keep class com.android.vending.billing.** { *; }

# ── Play Services Auth ──────────────────────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }

# ── Google API / Drive ──────────────────────────────────────────────────────
-keep class com.google.api.services.drive.** { *; }

# ── Reflection-heavy: RideCompletionHelper lambdas ──────────────────────────
-keep class com.elysium369.meet.ride.domain.** { *; }

# ── NFC / Vehicle Access ─────────────────────────────────────────────────────
-keep class com.elysium369.meet.core.vehicleaccess.** { *; }

# ── Widget ───────────────────────────────────────────────────────────────────
-keep class com.elysium369.meet.widget.** { *; }

# ── Play Billing Catalog product IDs ─────────────────────────────────────────
-keep class com.elysium369.meet.core.billing.** { *; }
