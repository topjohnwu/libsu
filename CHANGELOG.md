#2.3.3
- Update proguard rules to support R8 full mode
- Update gradle scripts to build proper Javadoc with links to Android reference

#2.3.2
- Fix a possible NPE in `SuFile`

# 2.3.1
- Publish aggregated Javadoc
- Strip out all deprecated APIs

# 2.3.0
Starting from 2.3.0, you shall stop using `Shell.Container`, including `ContainerApp`.

## Bug fixes
- Fix `Shell.Initializer` NPE on old API levels

## API Changes
- `Shell.Config.setContainer()` is deprecated. libsu will handle the global shell without any configurations
- `ContainerApp` is deprecated. The class is now just a stock `Application`

# 2.2.1
## Behavior Changes
- When using high level APIs (`Shell.su`, `Shell.sh`, `Shell.rootAccess()`), `NoShellException` is now silently suppressed to prevent unexpected crashes. Should no shell is possible to be created, `Shell.su/sh` will immediately return a `Shell.Result` with no output and show failure; `Shell.rootAccess()` will simply return `false`.

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
