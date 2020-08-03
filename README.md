# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu)

An Android library providing a complete solution for apps using root permissions.

`libsu` comes with 2 main components: the `core` module provides a robust API to interact with a Unix shell; the `service` module allows you to create root services to run Java/Kotlin and native C/C++ code (via JNI). The library handles the creation of the shell process, I/O with standard streams, multithreading, concurrency issues, and management of remote root services for you. With synchronous/asynchronous APIs and the concept of a globally shared shell session, this library makes integrating root into application logic very easy.

## [Changelog](./CHANGELOG.md)

## [Javadoc](https://javadoc.jitpack.io/com/github/topjohnwu/libsu/docs/3.0.0/javadoc/overview-summary.html)

## Download
```groovy
android {
    compileOptions {
        // This library uses Java 8 features, this is required
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    def libsuVersion = '3.0.0'

    // The core module is used by all other components
    implementation "com.github.topjohnwu.libsu:core:${libsuVersion}"

    // Optional: APIs for creating root services
    implementation "com.github.topjohnwu.libsu:service:${libsuVersion}"

    // Optional: For com.topjohnwu.superuser.io classes
    implementation "com.github.topjohnwu.libsu:io:${libsuVersion}"

    // Optional: Bundle prebuilt BusyBox binaries
    implementation "com.github.topjohnwu.libsu:busybox:${libsuVersion}"
}
```

## License
This project is licensed under the Apache License, Version 2.0. Please refer to `LICENSE` for the full text.

In the module `busybox`, a prebuilt BusyBox binary is included. BusyBox is licensed under GPLv2, please check its repository for full detail. The binaries included in the project are built with sources from [this repository](https://github.com/topjohnwu/ndk-busybox).

Theoretically, using a GPLv2 binary without linkage does not affect your app, so it should be fine to use it in closed source or other licensed projects as long as the source code of the binary itself is released (which I just provided), but **this is not legal advice**. Please consult legal experts if feeling concerned using the `busybox` module.

## Quick Tutorial
Please note that this is a rundown going through the key features of `libsu`. Please read the full Javadoc and check out the example app (`:example`) in this project for more details.

### Configuration
Set default configurations before the global `Shell` instance is created:

```java
// Somewhere such as Application or MainActivity
static {
    Shell.enableVerboseLogging = BuildConfig.DEBUG;
    Shell.setDefaultBuilder(Shell.Builder.create()
        .setFlags(Shell.FLAG_REDIRECT_STDERR)
        .setTimeout(10));
}
```

### Shell Operations
`Shell` operations can be performed through static `Shell.sh/su(...)` methods that use the globally shared shell session, or you can directly get a `Shell` object and use the instance APIs. Here we demo the former as it covers most use cases:

```java
Shell.Result result;
// Execute commands synchronously
result = Shell.su("find /dev/block -iname boot").exec();
// Aside from commands, you can also load scripts from InputStream
result = Shell.su(getResources().openRawResource(R.raw.script)).exec();

List<String> out = result.getOut();  // stdout
int code = result.getCode();         // return code of the last command
boolean ok = result.isSuccess();     // return code == 0?

// Async APIs
Shell.su("setenforce 0").submit();   // submit and don't care results
Shell.su("sleep 5", "echo hello").submit(result -> updateUI(result));

// Run tasks and output to specific Lists
List<String> logs = new ArrayList<>();
Shell.su("cat /cache/magisk.log").to(logs).exec();
List<String> stdout = new ArrayList<>();
List<String> stderr = new ArrayList<>();
Shell.su("echo hello", "echo hello >&2").to(stdout, stderr).exec();

// Receive output in real-time
List<String> callbackList = new CallbackList<String>() {
    @Override
    public void onAddElement(String s) { updateUI(s); }
};
Shell.su("for i in $(seq 5); do echo $i; sleep 1; done")
    .to(callbackList)
    .submit(result -> updateUI(result));
```

### Initialization
Optionally, to do some pre-configuration, initialize shells with custom `Shell.Initializer`:

```java
// Create something like bashrc
public class ExampleInitializer extends Shell.Initializer {
    @Override
    public boolean onInit(Context context, Shell shell) {
        InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc);
        // Here we use Shell instance APIs instead of sh/su(...) static methods
        shell.newJob()
            .add(bashrc)                  /* Load a script */
            .add("export ENV_VAR=VALUE")  /* And some commands */
            .exec();
        return true;  // Return false to indicate initialization failed
    }
}
Shell.Builder builder = ... ;
builder.setInitializers(ExampleInitializer.class);
```

### I/O
Built on top of the `core` foundation is a suite of I/O classes, re-creating `java.io` classes for root access. Use `File`, `RandomAccessFile`, `FileInputStream`, and `FileOutputStream` equivalents on files that are only accessible with root permissions. These classes internally use Unix commands through the global root shell, and the I/O streams are carefully optimized to have very promising performance. Add `com.github.topjohnwu.libsu:io` as a dependency to access root I/O classes:

```java
File logs = SuFile.open("/cache/magisk.log");
if (logs.exists()) {
    try (InputStream in = new SuFileInputStream(logs);
         OutputStream out = new SuFileOutputStream("/data/magisk.log.bak")) {
        // Do I/O stuffs...
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### Root Services (`minSdkVersion = 18`)
If interacting with a root shell and the I/O classes still do not serve your needs, you can also implement a root service to run complex code. A root service is similar to [Bound Services](https://developer.android.com/guide/components/bound-services) but running in a root process. `libsu` uses Android's native IPC mechanism, binder, for communication between your root service and the main application process. In addition to running Java/Kotlin code, loading native libraries with JNI is also supported (`android:extractNativeLibs=false` **is** allowed). Add `com.github.topjohnwu.libsu:service` as a dependency to access `RootService`:

```java
public class RootConnection implements ServiceConnection { ... }
public class ExampleService extends RootService {
	@Override
    public IBinder onBind(Intent intent) {
    	// return IBinder from Messenger or AIDL stub implementation
    }
}
RootConnection conn = new RootConnection();
Intent intent = new Intent(context, ExampleService.class);
RootService.bind(intent, conn);
```

`libsu` does NOT attempt to do messy workarounds for broken/outdated root solutions as I believe the responsibility shall not lie on the application side. This means there are no hacks for `sepolicy` live patching, `app_process` relocating etc. Any modern root solution with sane SELinux implementation should work: the library is tested on Magisk (supports API 17+), but the latest SuperSU on modern Android should also be fine.

### BusyBox
If you want to embed BusyBox directly in your app to ensure 100% reliable/reproducible shell environment, add `com.github.topjohnwu.libsu:busybox` as a dependency (`android:extractNativeLibs=false` is **NOT** compatible with the `busybox` module):

```java
Shell.Builder builder = ... ;
// Set BusyBoxInstaller as the first initializer.
builder.setInitializers(BusyBoxInstaller.class, /* other initializers */);
```

The BusyBox binaries are statically linked, feature complete, and includes full SElinux support. As a result they are pretty large in size (1.3 - 2.1 MB for each ABI). To reduce APK size, the best option is to use either [App Bundles](https://developer.android.com/guide/app-bundle) or [Split APKs](https://developer.android.com/studio/build/configure-apk-splits). If you are not publishing to Play Store, you can also limit your supporting ABIs:

```groovy
android {
  defaultConfig {
    ndk {
      // Filter your supported ABIs
      abiFilters 'armeabi-v7a'
    }
  }
}
```
