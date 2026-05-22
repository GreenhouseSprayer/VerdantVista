# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes AnnotationDefault, EnclosingMethod, InnerClasses, Signature, SourceFile, LineNumberTable
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent obfuscation of Data Models and API responses
# We need to keep the class names AND the members so GSON can map the JSON keys
-keep class Verdant.Vista.data.model.** { *; }
-keep class Verdant.Vista.data.api.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
