# Strip out debugging stuffs
-assumenosideeffects class com.topjohnwu.superuser.internal.Utils {
	public static void log(...);
	public static void ex(...);
	public static boolean vLog() return false;
	public static boolean hasStartupAgents(android.content.Context) return false;
}

# Keep classes referenced by reflection
-keep,allowobfuscation class * extends com.topjohnwu.superuser.Shell$Initializer { *; }
-keep,allowobfuscation class * extends com.topjohnwu.superuser.ipc.RootService { *; }
-keep class com.topjohnwu.superuser.Shell$Job
-keep class com.topjohnwu.superuser.Shell$Result
-keep class com.topjohnwu.superuser.Shell
