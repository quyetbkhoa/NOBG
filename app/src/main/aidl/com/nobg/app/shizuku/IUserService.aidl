package com.nobg.app.shizuku;

// Runs inside a process with Shizuku (shell UID) privileges.
// We simply expose shell command execution - shell UID already has
// permission for am force-stop / pm disable-user / pm enable / appops set,
// exactly like typing the same commands after `adb shell`.
interface IUserService {

    String exec(String cmd) = 1;

    void destroy() = 16777114; // Shizuku reserved destroy transaction
}
