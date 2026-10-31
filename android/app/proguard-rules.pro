# Project-specific R8 rules are added as features introduce reflection or serialization.

# AMap Maps SDK uses JNI and reflection internally.
-keep class com.amap.api.maps.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.api.trace.** { *; }
