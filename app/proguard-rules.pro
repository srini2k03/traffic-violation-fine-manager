# Navigation type-safe routes use kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.srini.trafficfine.**$$serializer { *; }
-keepclassmembers class com.srini.trafficfine.** {
    *** Companion;
}
-keepclasseswithmembers class com.srini.trafficfine.** {
    kotlinx.serialization.KSerializer serializer(...);
}
