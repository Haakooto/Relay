# R8 rules for release builds. Room, Glance, DataStore and kotlinx.serialization ship their own
# consumer rules; these cover our @Serializable classes (JSON columns: run plans, lap ops,
# clock and widget styles, schedule targets) the way the kotlinx.serialization docs recommend.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.relay.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.relay.app.**$$serializer { *; }
