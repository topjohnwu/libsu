# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu) \
[![](https://img.shields.io/badge/Javadoc-core-blue.svg)](https://jitpack.io/com/github/topjohnwu/libsu/core/latest/javadoc/)
[![](https://img.shields.io/badge/Javadoc-io-blue.svg)](https://jitpack.io/com/github/topjohnwu/libsu/io/latest/javadoc/)
[![](https://img.shields.io/badge/Javadoc-busybox-blue.svg)](https://jitpack.io/com/github/topjohnwu/libsu/busybox/latest/javadoc/)

An Android library that provides APIs to a Unix (root) shell.

Some poorly coded applications requests a new shell (call `su`, or worse `su -c <commands>`) for every single command, which is very inefficient. This library makes sharing a single, globally shared shell session in Android applications super easy: developers won't have to bother about concurrency issues, and with a rich selection of both synchronous and asynchronous APIs, it is much easier to create a powerful root app.

Optionally, `libsu` comes with a whole suite of I/O classes, re-creating `java.io` classes but enhanced with root access. Without even thinking about command-lines, you can use `File`, `RandomAccessFile`, `FileInputStream`, and `FileOutputStream` equivalents on all files that are only accessible with root permissions. The I/O stream classes are carefully optimized and have very promising performance.

Also optionally, this library bundles with prebuilt `busybox` binaries. App developers can easily setup and create an internal `busybox` environment without relying on potentially flawed (or even no) external `busybox`.

One complex Android application using `libsu` for all root related operations is [Magisk Manager](https://github.com/topjohnwu/Magisk/tree/master/app).

## Changelog

[Link to Changelog](./CHANGELOG.md)

## Download
```java
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    def libsuVersion = '2.2.0'
    implementation "com.github.topjohnwu.libsu:core:${libsuVersion}"

    /* Optional: For using com.topjohnwu.superuser.io classes */
    implementation "com.github.topjohnwu.libsu:io:${libsuVersion}"

    /* Optional: For including a prebuild busybox */
    implementation "com.github.topjohnwu.libsu:busybox:${libsuVersion}"
}
```

## Quick Tutorial

### Setup Container
If you don't extend `Application` in your app, directly use `ContainerApp` as application:
```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    ...>
    <application
        android:name="com.topjohnwu.superuser.ContainerApp"
        ...>
        ...
    </application>
</manifest>
```

Or if you use your own `Application` class, extend `ContainerApp`:
```java
public class MyApplication extends ContainerApp {
    static {
        // Set configurations in a static block
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.setTimeout(60);
    }
    ...  /* Other code */
}
```

Or if you cannot change you base class, here is a workaround:
```java
public class MyApplication extends CustomApplication {
    static {
        /* Set configurations in a static block */
        ...
    }
    // Create a new Container field to store the root shell
    private Shell.Container container;
    @Override
    public void onCreate() {
        super.onCreate();
        // Assign the container with a pre-configured Container
        container = Shell.Config.newContainer();
        ...  /* Other code */
    }
}
```

### Shell Operations
Once you have the container setup, you can directly use the high level APIs: `Shell.su()`/`Shell.sh()`:

```java
// Run commands and get output immediately
List<String> output = Shell.su("find /dev/block -iname boot").exec().getOut();

// Aside from commands, you can also load scripts from InputStream
Shell.Result result = Shell.su(getResources().openRawResource(R.raw.script)).exec();

// You can get more stuffs from the results
int code = result.getCode();
boolean ok = result.isSuccess();
output = result.getOut();

// Run commands and output to a specific List
List<String> logs = new ArrayList<>();
Shell.su("cat /cache/magisk.log").to(logs).exec();

// Run commands in the background and don't care results
Shell.su("setenforce 0").submit();

// Run commands in the background and get results via a callback
Shell.su("sleep 5", "echo hello").submit(result -> {
    /* This callback will be called on the main (UI) thread
     * after the operation is done (5 seconds after submit) */
    result.getOut();  /* Should return a list with a single string "hello" */
})

// Create a reactive callback List, and update the UI on each line of output
List<String> callbackList = new CallbackList<String>() {
    @MainThread
    @Override
    public void onAddElement(String s) {
        /* This callback will be called on the main (UI) thread each time
         * the list adds a new element (in this case: shell outputs a new line)*/
        uiUpdate(s);  /* Some method to update the UI */
    }
};
Shell.su(
    "for i in 1 2 3 4 5;do",
    "  echo $i"
    "  sleep 1"
    "done",
    "echo 'countdown done!'").to(callbackList).submit(result -> {
        /* Some stuffs cannot be acquired from callback lists
         * e.g. return codes */
        uiUpdate(result.getCode());
    });

// Also get STDERR
List<String> stdout = new ArrayList<>();
List<String> stderr = new ArrayList<>();
Shell.su("echo hello", "echo hello >&2").to(stdout, stderr).exec();
```

### I/O
Add `com.github.topjohnwu.libsu:io` as dependency to access the I/O wrapper classes:

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

### BusyBox
The I/O classes relies on several commandline tools. *Most* of the tools are availible in modern Android via `toybox` (Android 6+), however for compatibility and reliable/reproducible behavior (some applets included in `toybox` is not fully featured), it will be a good idea to have BusyBox included to the environment:

```java
/* If you want to bundle prebuilt busybox binaries with your app,
 * add com.github.topjohnwu.libsu:busybox as a dependency, and
 * register BusyBoxInstaller as an initializer to install the bundled BusyBox.
 *
 * Note that this will add 1.51 MB to your APK (compressed) */
Shell.Config.addInitializers(BusyBoxInstaller.class);

/* If your app only targets Magisk users, and you are not willing to
 * add the additional size for the busybox binaries, you can tell libsu
 * to use Magisk's internal busybox */
Shell.Config.setFlags(Shell.FLAG_USE_MAGISK_BUSYBOX);
```

### Advanced
Initialize shells with custom `Shell.Initializer`, similar to what `.bashrc` will do:

```java
class ExampleInitializer extends Shell.Initializer {
    @Override
    public boolean onInit(Context context, Shell shell) {
        try (InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc)) {
            // Load a script from raw resources
            shell.newJob()
                .add(bashrc)                            /* Load a script from raw resources */
                .add("export ENVIRON_VAR=SOME_VALUE")   /* Run some commands */
                .exec();
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}

// Register the class as initializer
Shell.Config.addInitializers(ExampleInitializer.class);
```

## Example

This repo also comes with an example app (`:example`), check the code and play/experiment with it.
