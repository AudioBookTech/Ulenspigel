# --- R8 / ProGuard Optimization Rules ---

# 1. JNI Protection
# Prevent R8 from obfuscating or removing any native methods.
# This ensures that the C++ side can still find the Java methods.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Explicitly protect the LyraDecoder class and its native methods.
# We also keep the 'nativeHandle' field as it's modified by native code.
-keep class com.KonstantinShramko.Ulenspigel.LyraDecoder {
    private long nativeHandle;
    private <methods>;
    public <methods>;
}

# 2. Media3 / AudioEngine Protection
# Protect the PlaybackState enum which might be used in logging or reflection.
-keep enum com.KonstantinShramko.Ulenspigel.PlaybackState { *; }

# 3. Serialization / DataStore
# If you use Gson or other reflection-based serializers, add rules here.
# For DataStore Preferences, usually no special rules are needed.

