# PulseIR ProGuard / R8 rules
-keepattributes *Annotation*, InnerClasses, Signature

# Keep kotlinx.serialization models, sealed hierarchies, enums, and generated serializers
-keep,includedescriptorclasses class com.pulseir.remote.data.**$$serializer { *; }
-keep,includedescriptorclasses class com.pulseir.remote.ir.**$$serializer { *; }
-keepclassmembers class com.pulseir.remote.data.** {
    *** Companion;
}
-keepclassmembers class com.pulseir.remote.ir.** {
    *** Companion;
}
-keepclasseswithmembers class com.pulseir.remote.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.pulseir.remote.ir.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.pulseir.remote.data.** { *; }
-keep class com.pulseir.remote.ir.** { *; }
