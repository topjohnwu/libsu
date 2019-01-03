# 2.2.0
Starting from this release, `libsu` is modularized into 3 parts: `core`, `io`, and `busybox`.

If you only use the shell implementation, you just need `com.github.topjohnwu.libsu:core`. `com.github.topjohnwu.libsu:io` and `com.github.topjohnwu.libsu:busybox` are optional: include the former to use the I/O wrapper classes, and the latter to bundle the prebuilt busybox binaries with your app. The old `com.github.topjohnwu:libsu` can still be used, but all 3 components will be pulled in.

## Bug fixes
- Clean up potential garbage output before testing shell
- Prevent possible `Shell.waitAndClose(int, TimeUnit)` race conditions

## API Changes
- Add support for multiple `Shell.Initializer`s: new methods `void Shell.Config.setInitializers(...)` and `void Shell.Config.addInitializers(...)` are added; `void Shell.Config.setInitializer(Initializer.class)` is deprecated.

## Breaking Changes
- Remove the class `BusyBox`. To install the prebuilt busybox, add `com.github.topjohnwu.libsu:busybox` as a dependency, and register `BusyBoxInstaller` as an initializer (`Shell.Config.addInitializers(BusyBoxInstaller.class);`)
- Introduce a new flag: `Shell.FLAG_USE_MAGISK_BUSYBOX`. With this flag set, `/sbin/.magisk/busybox` will be prepended to `PATH`, so the shell will use Magisk's internal busybox.

# 2.1.2
## Bug fixes
- Fix a bug that could cause `new SuFile(parent, name)` to fail

## Behavior Changes
- When creating `SuFileInputStream`/`SuFileOutputStream`/`SuRandomAccessFile` with a character file, it will throw `FileNotFoundException` instead of failing silently. Character file I/O is not possible with shells, use `Shell.su("cat <chr_file>").exec().getOut()` instead.

## API Changes
- Add `boolean Shell.waitAndClose(long, TimeUnit)` and `void Shell.waitAndClose()`. You can now wait for all tasks to finish before closing the shell instance.
- Add `Shell.Config.setTimeout(long)`. Use it to set the maximum time for waiting a new shell construction. The default value is 20 seconds.

# 2.0.3

## Improvements
- `Shell.isAlive()` method is updated to get process status directly via reflection rather than the traditional `Process.exitValue()` exception handling. This optimization significantly reduces the overhead when switching between Shell tasks.

## API Changes
- The 1.x version APIs are completely removed
- Retrolambda is removed, you should start using AGP 3.0.0+ which comes with official Java 8 desugaring

# 2.0.2

## Fixes
- Return proper list in `Shell.Result.getErr()`

# 2.0.1

## Fixes / Improvements
- Calling `submit` in `PendingJob` used to end up calling the overridden `exec` instead of `exec` in `JobImpl`. Proxy through a private method in `JobImpl` to prevent changed behavior of `JobImpl` subclasses.
- Even though the parameters of `Shell.Job.add(...)` is labeled `@NonNull`, it is still possible that developers still pass in `null` and cause NPE. Check `null` before proceed any further.
- Fix a bug that constructed shell instances weren't cached in the container when created using fallback methods (e.g. no root -> fallback to non-root shell). This would cause infinite loop when no root is available.
- Prevent `IllegalStateException` if the user provide a filter accepting `.` or `..` in `SuFile.list()` family methods.

# 2.0.0

Massive API improvements! Nearly all APIs in 1.x.x versions are deprecated. A compatibility layer is created to make the migration easier for existing developers: your existing code will work with `libsu` 2.0.0 without any modifications.

However, the compatibility layer **will** be removed in a future update, please do update your code to utilize the new APIs since it is much cleaner and more flexible!

### Incompatible API Changes
- Remove `readFully(InputStream, byte[])` and `readFully(InputStream, byte[], int, int)` from `ShellUtils`.

### Fixes / Improvements / Behavior Changes
- Global shell is now set before `Shell.Initializer` runs: this means you can now use high level APIs and root I/O classes in your initializer
- No more `null` lists will be returned from `libsu`: all methods will return empty lists if no output is available
- `SuFile.list()` family methods shall return hidden files now (filenames starting with `'.'`) (also fix #15)
- `SuFile` will use the tool `stat` in more methods for consistent results (fix #11)
- No longer uses raw threads and `AsyncTask.THREAD_POOL_EXECUTOR` for running code in background threads, switch to an internal `ExecutorService`
- `Shell.GetShellCallback.onShell(Shell)` will run on the main thread
- Add Proguard rules to strip out logging code when minify and optimization (`proguard-android-optimize.txt`) is enabled
- Lower `minSdkVersion` to 9

### API 2.0 Migration
Note: The following list is **ONLY** to point out a brief direction for migration, check the examples and documentation to know the details!

- All configuration methods are moved to the nested class `Shell.Config`
- Everything in `Shell.Sync` and `Shell.Async` is deprecated. Check the new APIs: `Shell.su(...)` and `Shell.sh(...)`
- New methods:
  - `Shell.getCachedShell()`: A static method to obtain the cached global root shell from the container, or return `null` of no active shell exists
  - `Shell.isRoot()`: Check root access of a root instance (Note: static method `Shell.rootAccess()` is used to check the global shell)
  - `Shell.Config.newContainer()`: construct a pre-configured shell container, used to simplify registering a container
- New classes:
  - `Shell.Job`: represent a job that outputs to a single result object. You can chain additional operations, assign output destination, execute or submit the job.
  - `Shell.Result`: stores the result of a `Shell.Job`. Includes STDOUT/STDERR, and something not existing in 1.x.x: return code
  - `Shell.ResultCallback`: a callback interface to get the result when you submit a `Shell.Job` to background threads
- For `Shell.Initializer`: deprecate `onShellInit(...)` and `onRootShellInit(....)`; use `onInit(Context, Shell)` instead: manually detect the shell status and handle Exceptions
- Move `Shell.ContainerApp` out of `Shell` to a separate class `ContainerApp`: easier to directly assign in `AndroidManifest.xml`
