## 3.1.1
#### Bug fixes
- Fix typo in `SuFile.length()`
- Escape filenames of the `File` argument in `SuFile.renameTo(File)`

## 3.1.0
### New Features
- On Android 5.0 and higher (API 21+), both `SuFileInputStream.open(...)` and `SuFileOutputStream.open(...)` return I/O streams backed by FIFO (named pipes). This provides 100% native file I/O stream performance and stability.
- On Android 4.4 and lower, `SuFileInputStream.open(...)` uses the old shell command backed `InputStream` as it was stress tested and proven reliable.
- On Android 4.4 and lower, `SuFileOutputStream.open(...)` will write all data to a temporary file in the application's cache folder, and will only actually output the data to the target location when the stream is closed. Please refer to Javadocs for more detail.
- If the internal copying of `SuFileOutputStream.open(...)` is unacceptable, `SuFileOutputStream.openNoCopy(...)` can be used to force the old implementation (shell command backed `OutputStream`) on Android 4.4 and lower. **However, according to stress test results, this implementation is error prone and I strongly recommend against using it.**
- If your `minSdkVersion` is 21 or higher (which most apps now are), these I/O stream changes basically improve performance and reliability for free without any complexities mentioned above.
- The `:busybox` module is updated with new busybox binaries (1.32.1). It also adds the logic to workaround older Samsung device kernel restrictions.

### Bug fixes
- Fix unaligned shell input (the bug did not affect `SuFileInputStream`)
- `SuFile` now properly escapes special characters of file names in internal implementations

### Deprecation
- Deprecated APIs in 3.0.x is removed
- Creating instances of `SuFileInputStream` and `SuFileOutputStream` is deprecated. Please use the static `SuFileInputStream.open(...)` and `SuFileOutputStream.open(...)` methods instead.

## 3.0.2
- Fix regression that could cause crashes on older Android versions when getting application context internally
- Add more nullability annotations for better Kotlin integration

## 3.0.1
New major release, introducing root service support!<br>
3.0.1 is fully source compatible with 2.6.0, but please migrate the deprecated methods as soon as possible as these shim will be removed soon.

### New Features
- New module `:service` is added: introduce `RootService` for remote root IPC
- `SuFileInputStream` now fully support `mark(int)`, `reset()`, and `skip(long)`
- `CallbackList` now support passing in a custom `Executor` in its constructor to configure which thread `onAddElement()` to run on

### Behavior Changes
- `CallbackList` no longer synchronizes its base `List` internally (if provided).
It is the developer's responsibility if synchronization is required

### API Changes
- `Shell.Builder` is now used to construct `Shell` objects. Each shell instance creation now has its own configurations
- `Shell.enableVerboseLogging` is now used to toggle verbose logging throughout the framework
- `Shell.setDefaultBuilder(Shell.Builder)` is now used to configure the global shell instance

### Deprecation
- `Shell.FLAG_VERBOSE_LOGGING`: use `Shell.enableVerboseLogging`
- `Shell.Config`: customize `Shell.Builder` and set it in `Shell.setDefaultBuilder(Shell.Builder)`
- `Shell.newInstance(...)`: create `Shell.Builder` and use `Shell.Builder.build(...)`

## 2.6.0
### API Changes
- New APIs to allow users to customize which thread to dispatch when returning results via callbacks
    - `Shell.getShell(Executor, GetShellCallback)`
    - `Shell.Job.submit(Executor, GetShellCallback)`

### Improvements
- `Shell.su/sh(...).submit(...)` will no longer switch back to the main thread internally, reducing unnecessary thread hopping

### Behavior Changes
- The bundled BusyBox now utilizes "Standalone Mode ASH", which forces all commands to use BusyBox applets.
For more info please read the Javadoc for `BusyBoxInstaller`.
- The bundled BusyBox now supports full SELinux features

### Breaking Changes
- All deprecated APIs in 2.5.2 are removed

## 2.5.2
### Improvements
- Be more conservative with synchronizing internally
- Use a more efficient SerialExecutorService implementation
- Allow users to set their own ExecutorService for libsu (`Shell.EXECUTOR`)
- Some minor optimizations

### Deprecation
- All deprecated methods/fields/classes will be removed in the next release
- `ShellUtils.pump(InputStream, OutputStream)`
- `ShellUtils.noFlushPump(InputStream, OutputStream)`
- `ShellUtils.checkSum(String, File, String)`
- `Shell.FLAG_USE_MAGISK_BUSYBOX`
- `Shell.Config.addInitializers(...)`
- `Shell.Config.getFlags()`
- `SuProcessFileInputStream`
- `SuProcessFileOutputStream`

## 2.5.1
### Improvements / Bug fixes
- `SuFile.getParent()` no longer cause NPE when no parent exists
- The `libsu:busybox` module now includes prebuilt busybox binaries as native libraries.
This properly conforms with Play Store rules to ship executables along with APKs.
All 4 ABIs (armeabi-v7a, arm64-v8a, x86, x86_64) are included; utilize app bundles for smaller app download sizes.
- More nullability annotations for better Kotlin integration.
- Some minor internal implementation improvements

### API Changes
- `ShellUtils.genRandomAlphaNumString(int)` is removed

## 2.5.0
### Behavior Changes
- `SuFile` will now follow symbolic links (as it always should)
- `SuFile.length()` will no longer report block total size on block devices (as it shouldn't in the first place)
- `SuFileInputStream` and `SuFileOutputStream` now supports I/O on all file formats, including character files (e.g. files in `/sys` and `/proc`), and also outputs to block devices correctly

### API Changes
- Make `SuFile.isBlock()`, `SuFile.isCharacter()`, `SuFile.isSymlink()` public

### Improvements
- Tons internal optimizations to improve performance
- Rewrite shell backed I/O from scratch
- Added more detailed Javadoc for `SuFile`

## 2.4.0
### Behavior Changes
- `SuFile` is no longer a wrapper class around `File`. Calling the constructor directly will directly open a shell backed `File` instance. This change is due to the fact that `File` wrapper class causes some issues in methods like `File.renameTo(File)`.

### API Changes
- Introduce new helper methods: `SuFile.open(...)`: depending on whether the global shell has root access or not, these methods will return either a normal `File` instance or `SuFile`. This is the same behavior as the previous `SuFile` constructor (the only difference is that `SuFile` used to wrap around the instance).

## 2.3.3
- Update proguard rules to support R8 full mode
- Update gradle scripts to build proper Javadoc with links to Android reference

## 2.3.2
- Fix a possible NPE in `SuFile`

## 2.3.1
- Publish aggregated Javadoc
- Strip out all deprecated APIs

## 2.3.0
Starting from 2.3.0, you shall stop using `Shell.Container`, including `ContainerApp`.

### Bug fixes
- Fix `Shell.Initializer` NPE on old API levels

### API Changes
- `Shell.Config.setContainer()` is deprecated. libsu will handle the global shell without any configurations
- `ContainerApp` is deprecated. The class is now just a stock `Application`

## 2.2.1
### Behavior Changes
- When using high level APIs (`Shell.su`, `Shell.sh`, `Shell.rootAccess()`), `NoShellException` is now silently suppressed to prevent unexpected crashes. Should no shell is possible to be created, `Shell.su/sh` will immediately return a `Shell.Result` with no output and show failure; `Shell.rootAccess()` will simply return `false`.

## 2.2.0
Starting from this release, `libsu` is modularized into 3 parts: `core`, `io`, and `busybox`.

If you only use the shell implementation, you just need `com.github.topjohnwu.libsu:core`. `com.github.topjohnwu.libsu:io` and `com.github.topjohnwu.libsu:busybox` are optional: include the former to use the I/O wrapper classes, and the latter to bundle the prebuilt busybox binaries with your app. The old `com.github.topjohnwu:libsu` can still be used, but all 3 components will be pulled in.

### Bug fixes
- Clean up potential garbage output before testing shell
- Prevent possible `Shell.waitAndClose(int, TimeUnit)` race conditions

### API Changes
- Add support for multiple `Shell.Initializer`s: new methods `void Shell.Config.setInitializers(...)` and `void Shell.Config.addInitializers(...)` are added; `void Shell.Config.setInitializer(Initializer.class)` is deprecated.

### Breaking Changes
- Remove the class `BusyBox`. To install the prebuilt busybox, add `com.github.topjohnwu.libsu:busybox` as a dependency, and register `BusyBoxInstaller` as an initializer (`Shell.Config.addInitializers(BusyBoxInstaller.class);`)
- Introduce a new flag: `Shell.FLAG_USE_MAGISK_BUSYBOX`. With this flag set, `/sbin/.magisk/busybox` will be prepended to `PATH`, so the shell will use Magisk's internal busybox.

## 2.1.2
### Bug fixes
- Fix a bug that could cause `new SuFile(parent, name)` to fail

### Behavior Changes
- When creating `SuFileInputStream`/`SuFileOutputStream`/`SuRandomAccessFile` with a character file, it will throw `FileNotFoundException` instead of failing silently. Character file I/O is not possible with shells, use `Shell.su("cat <chr_file>").exec().getOut()` instead.

### API Changes
- Add `boolean Shell.waitAndClose(long, TimeUnit)` and `void Shell.waitAndClose()`. You can now wait for all tasks to finish before closing the shell instance.
- Add `Shell.Config.setTimeout(long)`. Use it to set the maximum time for waiting a new shell construction. The default value is 20 seconds.

## 2.0.3

### Improvements
- `Shell.isAlive()` method is updated to get process status directly via reflection rather than the traditional `Process.exitValue()` exception handling. This optimization significantly reduces the overhead when switching between Shell tasks.

### API Changes
- The 1.x version APIs are completely removed
- Retrolambda is removed, you should start using AGP 3.0.0+ which comes with official Java 8 desugaring

## 2.0.2

### Fixes
- Return proper list in `Shell.Result.getErr()`

## 2.0.1

### Fixes / Improvements
- Calling `submit` in `PendingJob` used to end up calling the overridden `exec` instead of `exec` in `JobImpl`. Proxy through a private method in `JobImpl` to prevent changed behavior of `JobImpl` subclasses.
- Even though the parameters of `Shell.Job.add(...)` is labeled `@NonNull`, it is still possible that developers still pass in `null` and cause NPE. Check `null` before proceed any further.
- Fix a bug that constructed shell instances weren't cached in the container when created using fallback methods (e.g. no root -> fallback to non-root shell). This would cause infinite loop when no root is available.
- Prevent `IllegalStateException` if the user provide a filter accepting `.` or `..` in `SuFile.list()` family methods.
