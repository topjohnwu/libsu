# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu)

An Android library that provides APIs to a Unix (root) shell.

Some poorly coded applications requests a new shell (call `su`, or worse `su -c <commands>`) for every single command, which is very inefficient. This library makes sharing a single, globally shared shell session in Android applications super easy: developers won't have to bother about concurrency issues, and with a rich selection of both synchronous and asynchronous APIs, it is much easier to create a powerful root app.

This library bundles with full featured `busybox` binaries. App developers can easily setup and create an internal `busybox` environment with the built-in helper method without relying on potentially flawed (or even no) external `busybox`.

`libsu` also comes with a whole suite of I/O classes, re-creating `java.io` classes but enhanced with root access. Without even thinking about command-lines, you can use `File`, `RandomAccessFile`, `FileInputStream`, and `FileOutputStream` equivalents on all files that are only accessible with root permissions. The I/O stream classes are carefully optimized and have very promising performance.

One complex Android application using `libsu` for all root related operations is [Magisk Manager](https://github.com/topjohnwu/Magisk/tree/master/app).

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
    @Override
    public void onCreate() {
        super.onCreate();

        // You can configure Shell here
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);

        // Use libsu's internal BusyBox
        BusyBox.setup(this);

        /* Your other code */
        ...
    }
}
```

Or if you cannot change you base class, here is a workaround:
```java
public class MyApplication extends CustomApplication {
    // Create a new Container field to store the root shell
    private Shell.Container container;
    @Override
    public void onCreate() {
        super.onCreate();
        // Assign the container with a pre-configured Container
        container = Shell.Config.newContainer();

        /* Configure shell and your other code */
        ...
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
Shell.su("setenfoce 0").submit();

// Run commands in the background and get results via a callback
Shell.su("sleep 5", "echo hello").submit(result -> {
    /* This callback will be called on the main (UI) thread
     * after the operation is done (5 seconds after submit) */
    result.getOut();  /* Should return a list with a single string "hello" */
})

// Create a reactive callback List, and update the UI on each line of output
List<String> callbackList = new CallbackList<String>() {
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
class ExampleInitializer extends Shell.Initializer {
    @Override
    public boolean onInit(Context context, Shell shell) {
        try (InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc)) {
            // Load a script from raw resources
            shell.newJob()
                .add(bashrc)                            /* Load a script from raw resources */
                .add("export PATH=/custom/path:$PATH")  /* Run some commands */
                .exec();
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}

// Register the class as initializer
Shell.Config.setInitializer(ExampleInitializer.class);
```

## Documentation

This repo also comes with an example app (`:example`), check the code and play/experiment with it.

I strongly recommend all developers to check out the more detailed full documentation: [JavaDoc Page](https://topjohnwu.github.io/libsu).
