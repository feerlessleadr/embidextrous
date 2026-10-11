# kotlinx.serialization keeps what it needs through its own consumer rules.
-keepattributes *Annotation*, InnerClasses
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# The API models are tiny and read through kotlinx.serialization; keep them intact rather than risk a shrunk serializer.
-keep class com.feerlessleadr.embidextrous.data.** { *; }
