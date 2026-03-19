# Add project specific ProGuard rules here.

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.najmi.corvus.**$$serializer { *; }
-keepclassmembers class com.najmi.corvus.** {
    *** Companion;
}
-keepclasseswithmembers class com.najmi.corvus.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**

# Keep data classes
-keep class com.najmi.corvus.data.remote.** { *; }
-keep class com.najmi.corvus.domain.model.** { *; }
