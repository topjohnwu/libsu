# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu)

An Android library that provides APIs to a Unix (root) shell.

Some poorly coded applications requests a new shell (call `su`, or worse `su -c <commands>`) for every single command, which is very inefficient. This library makes sharing a single, globally shared shell session in Android applications super easy: developers don't have to worry about concurrency issues, and it comes with a rich selection of both synchronous and asynchronous APIs to create a powerful root app.

One complex Android application using `libsu` for all root related operations is [Magisk Manager](https://github.com/topjohnwu/MagiskManager).

## Download
```java
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.topjohnwu:libsu:1.0.1'
}
```

## Simple Tutorial

### Setup
Subclass `Shell.ContainerApp` and use it as your `Application`:

```java
public class ExampleApp extends Shell.ContainerApp {
    public ExampleApp() {
        // Set some flags here if you need
        Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.verboseLogging(BuildConfig.DEBUG);
    }
}
```

Specify the custom Application in `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
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

### Advanced
Initialize the shell with custom `Shell.Initializer`, similar to what `.bashrc` will do.

```java
Shell.setInitializer(new Shell.Initializer() {
    @Override
    public void onRootShellInit(@NonNull Shell shell) {
        shell.run(null, null, "export PATH=" + BUSYBOX_PATH + ":$PATH");
    }
});
```

## Documentation

This repo also comes with an example app (`:example`), check the code and play/experiment with it.

More detailed full documentation is in the project's [JavaDoc Page](https://topjohnwu.github.io/libsu).
