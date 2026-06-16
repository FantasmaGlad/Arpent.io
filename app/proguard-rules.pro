# Préservation des métadonnées Kotlin et annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keep @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** $serializer;
}
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}

# Supabase & Ktor Client
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.client.** { *; }

# Mapbox Maps SDK
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep interface * extends androidx.room.Dao { *; }
-keep class * extends androidx.room.TypeConverter { *; }
