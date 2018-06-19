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

