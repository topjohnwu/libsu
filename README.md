# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu)

An Android library that provides APIs to a Unix (root) shell.

Some poorly coded applications requests a new shell (call `su`, or worse `su -c <commands>`) for every single command, which is very inefficient. This library makes sharing a single, globally shared shell session in Android applications super easy: developers won't have to bother about concurrency issues, and with a rich selection of both synchronous and asynchronous APIs, it is much easier to create a powerful root app.

This library bundles with full featured `busybox` binaries. App developers can easily setup and create an internal `busybox` environment with the built-in helper method without relying on potentially flawed (or even no) external `busybox`.

`libsu` also comes with a whole suite of I/O classes, re-creating `java.io` classes but enhanced with root access. Without even thinking about command-lines, you can use `File`, `RandomAccessFile`, `FileInputStream`, and `FileOutputStream` equivalents on all files that are only accessible with root permissions. The I/O stream classes are carefully optimized and have very promising performance.

One complex Android application using `libsu` for all root related operations is [Magisk Manager](https://github.com/topjohnwu/MagiskManager).

## Changelog

[Link to Changelog](./CHANGELOG.md)

## Download
```java
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.topjohnwu:libsu:1.3.0'
}
```

## Simple Tutorial

### Setup
Subclass `Shell.ContainerApp` and use it as your `Application`.  
Set flags, initializers as soon as possible:

```java
public class ExampleApp extends Shell.ContainerApp {
    @Override
    public void onCreate() {
        super.onCreate();
        // Set flags
        Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.verboseLogging(BuildConfig.DEBUG);
    }
}
```

Specify the custom Application in `AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    ...>
    <application
        android:name=".ExampleApp"
        ...>
        ...
    </application>
</manifest>
```

### Synchronous Shell Operations

High level synchronous APIs are under `Shell.Sync`. Think twice before calling these methods in the main thread, as they could cause the app to freeze and end up with ANR errors.

```java
/* Simple root shell commands, get results immediately */
List<String> result = Shell.Sync.su("find /dev/block -iname 'boot'");

/* Do something with the result */

/* Execute scripts from raw resources */
result = Shell.Sync.loadScript(getResources().openRawResource(R.raw.script)));
```

### Asynchronous Shell Operations

High level asynchronous APIs are under `Shell.Async`. These methods will return immediately and will not get the output synchronously. Use callbacks to receive the results, or update UI asynchronously.

```java
/* Run commands and don't care the result */
Shell.Async.su("setenforce 0", "setprop test.prop test");

/* Get results after commands are done with a callback */
Shell.Async.su(new Shell.Async.Callback() {
    @Override
    public void onTaskResult(List<String> out, List<String> err) {
        /* Do something with the result */
    }
    @Override
    public void onTaskError(Throwable err) {
        /* Throwable thrown, handle the error */
    }
}, "cat /proc/mounts");

/* Use a CallbackList to receive a callback every time a new line is outputted */

List<String> callbackList = new CallbackList<String>() {
    @Override
    public void onAddElement(String s) {
        /* Do something with the new line */
    }
};

// Pass the callback list to receive shell outputs
Shell.Async.su(callbackList, "for i in 1 2 3 4 5; do echo $i; sleep 1; done");
```

### I/O
`libsu` also comes with a rich suite of I/O classes for developers to access files using the shared root shell:

```java
/* Treat files that require root access just like ordinary files */
SuFile logs = new SuFile("/cache/magisk.log");
if (logs.exists()) {
    try (InputStream in = new SuFileInputStream(logs);
         OutputStream out = new SuFileOutputStream("/data/magisk.log.bak")) {
        /* All file data can be accessed by Java Streams */

        // For example, use a helper method to copy the logs
        ShellUtils.pump(in, out);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### Advanced
Initialize the shell with custom `Shell.Initializer`, similar to what `.bashrc` will do.

```java
Shell.setInitializer(ExampleInitializer.class);

class ExampleInitializer extends Shell.Initializer {
    @Override
    public boolean onRootShellInit(Context context, Shell shell) throws IOException {
        // Use internal busybox
        BusyBox.setup(context);
        try (InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc)) {
            // Load a script from raw resources
            shell.loadInputStream(null, null, bashrc);
        }
        return true;
    }
}
```

## Documentation

This repo also comes with an example app (`:example`), check the code and play/experiment with it.

I strongly recommend all developers to check out the more detailed full documentation: [JavaDoc Page](https://topjohnwu.github.io/libsu).
