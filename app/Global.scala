import java.security.Permission

import play.api.{Application, GlobalSettings}

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    val securityManager = new SecurityManager() {
      override def checkPermission(permission: Permission) {
        if (permission.getName.startsWith("exitVM")) {
          throw new SecurityException("exitVM")
        }
      }
    }
    System.setSecurityManager(securityManager)
  }
}
