# PulseIR ProGuard / R8 rules
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.pulseir.remote.data.** { *; }
-keepclassmembers class com.pulseir.remote.ir.** { *; }
