import java.security.Permission

import io.PerThreadPrintStream
import play.api.{Application, GlobalSettings}

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    // Stricter security manager to limit what simulation can do
    // TODO limit it to simulation code only (via ThreadLocal?)
    val securityManager = new SecurityManager() {
      override def checkPermission(permission: Permission) {
        if (permission.getName.startsWith("exitVM")) {
          throw new SecurityException("exitVM")
        }
      }
    }
    System.setSecurityManager(securityManager)

    // Redirect simulation stdout/stderr.
    System.setOut(PerThreadPrintStream.out)
    System.setErr(PerThreadPrintStream.err)
  }
}
