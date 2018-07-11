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

