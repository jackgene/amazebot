package lifecycle

import com.google.inject.AbstractModule
import io.PerThreadPrintStream
import javax.inject.Singleton

@Singleton
class LifeCycle {
  // Redirect simulation stdout/stderr.
  System.setOut(PerThreadPrintStream.out)
  System.setErr(PerThreadPrintStream.err)
}
class LifeCycleModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[LifeCycle]).asEagerSingleton()
  }
}