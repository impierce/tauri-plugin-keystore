# Rules propagated to apps that consume this plugin.
#
# Command dispatch and argument parsing are both reflective: Tauri looks up
# @Command methods on the @TauriPlugin class by name, and Jackson populates the
# @InvokeArg classes from JSON. R8 cannot see either usage, so without these
# rules a consuming app's release build can strip or rename them and every
# command fails at runtime while debug builds keep working.

-keep @app.tauri.annotation.TauriPlugin public class app.tauri.keystore.** {
  @app.tauri.annotation.Command public <methods>;
  public <init>(...);
}

-keep @app.tauri.annotation.InvokeArg public class app.tauri.keystore.** {
  *;
}
