# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Strip out debugging stuffs
-assumenosideeffects class com.topjohnwu.superuser.internal.Utils {
	public static void log(...);
	public static void ex(...);
	public static boolean vLog() return false;
	public static boolean hasStartupAgents(android.content.Context) return false;
}
-assumenosideeffects class android.os.Debug {
	public static boolean isDebuggerConnected() return false;
}

# Make sure R8/Proguard don't break things
-keep,allowobfuscation class * extends com.topjohnwu.superuser.Shell$Initializer { *; }
-keep,allowobfuscation class * extends com.topjohnwu.superuser.ipc.RootService { *; }
