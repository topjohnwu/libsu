# libsu

[![](https://jitpack.io/v/topjohnwu/libsu.svg)](https://jitpack.io/#topjohnwu/libsu)

An Android library providing a complete solution for apps using root permissions.

`libsu` comes with 2 main components: the `core` module handles the creation of the Unix (root) shell process and wraps it with high level, robust Java APIs; the `service` module handles the launching, binding, and management of root services over IPC, allowing you to run Java/Kotlin and C/C++ code (via JNI) with root permissions.

## [Changelog](./CHANGELOG.md)

## [Javadoc](https://topjohnwu.github.io/libsu/)

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
    def libsuVersion = '4.0.2'

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

## Quick Tutorial

Please note that this is a quick demo going through the key features of `libsu`. Please read the full Javadoc and check out the example app (`:example`) in this project for more details.

### Configuration

Similar to threads where there is a special "main thread", `libsu` also has the concept of the "main shell". For each process, there is a single globally shared "main shell" that is constructed on-demand and cached. Set default configurations before the main `Shell` instance is created:

```java
public class SplashActivity extends Activity {

    static {
        // Set settings before the main shell can be created
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Preheat the main root shell in the splash screen
        // so the app can use it afterwards without interrupting
        // application flow (e.g. root permission prompt)
        Shell.getShell(shell -> {
            // The main shell is now constructed and cached
            // Exit splash screen and enter main activity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }
}

```

### Shell Operations

`Shell` operations can be performed through static `Shell.cmd(...)` methods that directly use the main root shell:

```java
Shell.Result result;
// Execute commands synchronously
result = Shell.cmd("find /dev/block -iname boot").exec();
// Aside from commands, you can also load scripts from InputStream.
// This is NOT like executing a script like "sh script.sh", but rather
// more similar to sourcing the script (". script.sh").
result = Shell.cmd(getResources().openRawResource(R.raw.script)).exec();

List<String> out = result.getOut();  // stdout
int code = result.getCode();         // return code of the last command
boolean ok = result.isSuccess();     // return code == 0?

// Async APIs
Shell.cmd("setenforce 0").submit();   // submit and don't care results
Shell.cmd("sleep 5", "echo hello").submit(result -> updateUI(result));

// Run tasks and output to specific Lists
List<String> mmaps = new ArrayList<>();
Shell.cmd("cat /proc/1/maps").to(mmaps).exec();
List<String> stdout = new ArrayList<>();
List<String> stderr = new ArrayList<>();
Shell.cmd("echo hello", "echo hello >&2").to(stdout, stderr).exec();

// Receive output in real-time
List<String> callbackList = new CallbackList<String>() {
    @Override
    public void onAddElement(String s) { updateUI(s); }
};
Shell.cmd("for i in $(seq 5); do echo $i; sleep 1; done")
    .to(callbackList)
    .submit(result -> updateUI(result));
```

### Initialization

Optionally, a similar concept to `.bashrc`, initialize shells with custom `Shell.Initializer`:

```java
public class ExampleInitializer extends Shell.Initializer {
    @Override
    public boolean onInit(Context context, Shell shell) {
        InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc);
        // Here we use Shell instance APIs instead of Shell.cmd(...) static methods
        shell.newJob()
            .add(bashrc)                  /* Load a script */
            .add("export ENV_VAR=VALUE")  /* Run some commands */
            .exec();
        return true;  // Return false to indicate initialization failed
    }
}
Shell.Builder builder = /* Create a shell builder */ ;
builder.setInitializers(ExampleInitializer.class);
```

### I/O

Built on top of the `core` foundation is a suite of I/O classes, re-creating `java.io` classes for root access. Use `File`, `FileInputStream`, and `FileOutputStream` equivalents on files that are only accessible with root permissions. Add `com.github.topjohnwu.libsu:io` as a dependency to access root I/O classes:

```java
File bootBlock = SuFile.open("/dev/block/by-name/boot");
if (bootBlock.exists()) {
    try (InputStream in = SuFileInputStream.open(bootBlock);
         OutputStream out = SuFileOutputStream.open("/data/boot.img")) {
        // Do I/O stuffs...
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### Root Services

If interacting with a root shell is too limited for your needs, you can also implement a root service to run complex code. A root service is similar to [Bound Services](https://developer.android.com/guide/components/bound-services) but running in a root process. `libsu` uses Android's native IPC mechanism, binder, for communication between your root service and the main application process. In addition to running Java/Kotlin code, loading native libraries with JNI is also supported (`android:extractNativeLibs=false` **is** allowed). For more details, please read the full Javadoc of `RootService` and check out the example app for more details. Add `com.github.topjohnwu.libsu:service` as a dependency to access `RootService`:

```java
public class RootConnection implements ServiceConnection { ... }
public class ExampleService extends RootService {
    @Override
    public IBinder onBind(Intent intent) {
        // return IBinder from Messenger or AIDL stub implementation
    }
}
RootConnection connection = new RootConnection();
Intent intent = new Intent(context, ExampleService.class);
RootService.bind(intent, connection);
```

##### Debugging Root Services

If the application process creating the root service has a debugger attached, the root service will automatically enable debugging mode and wait for the debugger to attach. In Android Studio, go to **"Run > Attach Debugger to Android Process"**, tick the **"Show all processes"** box, and you should be able to manually attach to the remote root process. Currently, only the **"Java only"** debugger is supported.

### BusyBox

It is recommended to utilize root services instead of relying on the shell environment for your application. However, if you still want to embed BusyBox directly in your app to ensure a 100% reproducible shell environment, add `com.github.topjohnwu.libsu:busybox` as a dependency (`android:extractNativeLibs=false` is **NOT** compatible with the `busybox` module):

```java
Shell.Builder builder = /* Create a shell builder */ ;
// Set BusyBoxInstaller as the first initializer
builder.setInitializers(BusyBoxInstaller.class, /* other initializers */);
```

The BusyBox binaries are statically linked, feature complete, and includes full SElinux support. As a result they are pretty large in size (1.3 - 2.1 MB for each ABI). To reduce APK size, the best option is to use either [App Bundles](https://developer.android.com/guide/app-bundle) or [Split APKs](https://developer.android.com/studio/build/configure-apk-splits).

## License

This project is licensed under the Apache License, Version 2.0. Please refer to `LICENSE` for the full text.

In the module `busybox`, prebuilt BusyBox binaries are included. BusyBox is licensed under GPLv2, please check its repository for full detail. The binaries included in the project are built with sources from [this repository](https://github.com/topjohnwu/ndk-busybox).

Theoretically, using a GPLv2 binary without linkage does not affect your app, so it should be fine to use it in closed source or other licensed projects as long as the source code of the binary itself is released (which I just provided), but **this is not legal advice**. Please consult legal experts if feeling concerned using the `busybox` module.
