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
    def libsuVersion = '5.0.3'

    // The core module that provides APIs to a shell
    implementation "com.github.topjohnwu.libsu:core:${libsuVersion}"

    // Optional: APIs for creating root services. Depends on ":core"
    implementation "com.github.topjohnwu.libsu:service:${libsuVersion}"

    // Optional: Provides remote file system support
    implementation "com.github.topjohnwu.libsu:nio:${libsuVersion}"
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

### I/O

Add `com.github.topjohnwu.libsu:nio` as a dependency to access remote file system APIs:

```java
// Create the file system service in the root process
// For example, create and send the service back to the client in a RootService
public class ExampleService extends RootService {
    @Override
    public IBinder onBind(Intent intent) {
        return FileSystemManager.getService();
    }
}

// In the client process
IBinder binder = /* From the root service connection */;
FileSystemManager remoteFS;
try {
    remoteFS = FileSystemManager.getRemote(binder);
} catch (RemoteException e) {
    // Handle errors
}
ExtendedFile bootBlock = remoteFS.getFile("/dev/block/by-name/boot");
if (bootBlock.exists()) {
    ExtendedFile bootBackup = remoteFS.getFile("/data/boot.img");
    try (InputStream in = bootBlock.newInputStream();
         OutputStream out = bootBackup.newOutputStream()) {
        // Do I/O stuffs...
    } catch (IOException e) {
        // Handle errors
    }
}
```

## License

This project is licensed under the Apache License, Version 2.0. Please refer to `LICENSE` for the full text.
