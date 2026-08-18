# Add project specific ProGuard rules here.
-keep class com.afyzfur.afyzhub.** { *; }
-keepclassmembers class com.afyzfur.afyzhub.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.afyzfur.afyzhub.**$$serializer { *; }
-keepclassmembers class com.afyzfur.afyzhub.** {
    *** Companion;
}
-keepclasseswithmembers class com.afyzfur.afyzhub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**