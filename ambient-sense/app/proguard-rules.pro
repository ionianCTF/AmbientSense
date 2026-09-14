# Ambient Sense — release shrink rules

# Room: keep schema/dao generated impls (Room ships its own consumer rules, kept explicit for safety)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# osmdroid / OSM tile provider uses reflection for tile sources
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Kotlin serialization of our own model classes is not used; keep data classes intact for Room
-keepclassmembers class com.ambientsense.app.data.** { <fields>; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep model classes used by Compose state
-keep class com.ambientsense.app.model.** { *; }
