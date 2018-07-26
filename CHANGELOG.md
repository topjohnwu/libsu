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

# 1.3.0

### Incompatible API Changes
- `execSyncTask`, `execAsyncTask`, `createCmdTask`, and `createLoadStreamTask` is removed from the non-static APIs of `Shell`. These are implementation details that weren't supposed to be part of the API


### Behavior Changes
- `open` of `SuRandomAccessFile` now supports a new parameter, `mode`, just like the constructor of `RandomAccessFile`
  - Deprecate: `SuRandomAccessFile.open(File)`  
    Recommend: `SuRandomAccessFile.open(File, String)`
  - Deprecate: `SuRandomAccessFile.open(String)`  
    Recommend: `SuRandomAccessFile.open(String, String)`
- `onRootShellInit` of `Shell.Initializer` can now run in BusyBox environment if `BusyBox.setup(Context)` is invoked or `BusyBox.BB_PATH` is set before any `Shell` will be constructed
- `SuFile` will become a wrapper around standard `File` if no root is available
- `SuFileInputStream` and `SuFileOutputStream` will throw `FileNotFoundException` when no root is available, and opening with standard `FileInputStream` / `FileOutputStream` throws `FileNotFoundException`
- `Shell` will not be forcibly closed when an `Exception` is thrown in `Shell.Task.run`

### Improvements
- The `minSdkVersion` of `libsu` is actually 11, changed accordingly

# 1.2.0

### Incompatible API Changes
- New required callback: `void Shell.Async.Callback.onTaskError(Throwable)`
- Remove unnecessary method: `boolean Shell.testCmd(String)`

### Behavior Changes
- `SuFile` now always uses `Shell` for all operations:
  - Deprecate constructor: `SuFile(String, boolean)`
  - Deprecate constructor: `SuFile(File, boolean)`
- `CallbackList.onAddElement(E)` will always run on the main thread.
- All callbacks in `Shell.Async.Callback` will always run on the main thread.
- Errors while executing operations are returned:
  - `Shell.run(List, List, String...)` now returns `Throwable`
  - `Shell.loadInputStream(List, List, InputStream)` now returns `Throwable`

### New Implementations
- New `Shell.Initializer` implementation:
  - Deprecate: `void Shell.setInitializer(Shell.Initializer)`  
    Recommend: `void Shell.setInitializer(Class<? extends Shell.Initializer>)`
  - Deprecate: `void Shell.Initializer.onShellInit(Shell)`  
    Recommend: `boolean Shell.Initializer.onShellInit(Context, Shell) throws Exception`
  - Deprecate: `void Shell.Initializer.onRootShellInit(Shell)`  
    Recommend: `boolean Shell.Initializer.onRootShellInit(Context, Shell) throws Exception`

### Improvements
- Fix BusyBox environment creation on some devices 
- Bump internal BusyBox to 1.28.4
- Update `SuFile` implementation to prevent filename clashing with shell commands

