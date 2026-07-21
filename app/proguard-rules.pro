# Project-specific R8 rules.
#
# Dependencies such as Room, Retrofit, WorkManager and kotlinx.serialization
# publish their own consumer rules. Keep this file minimal and add rules only
# when an actual release build or runtime test demonstrates that they are needed.

# Preserve metadata used by reflection-based libraries and useful crash traces.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
