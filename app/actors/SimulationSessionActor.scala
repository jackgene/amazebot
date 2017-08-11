package actors

import java.lang.reflect.Method

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.codehaus.commons.compiler.CompilerFactoryFactory

/**
  * Maintains state for a single simulation session (page load).
  *
  * @author Jack Leow
  * @since August 2017
  */
object SimulationSessionActor {
  def props(webSocketOut: ActorRef): Props = {
    Props(classOf[SimulationSessionActor], webSocketOut)
  }
}
class SimulationSessionActor(webSocketOut: ActorRef) extends Actor with ActorLogging {
  private val PackageNameExtractor =
    """(?s)\s*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  override def receive: Receive = {
    case javaSource: String =>
      val PackageNameExtractor(packageName: String) = javaSource
      val ClassNameExtractor(className: String) = javaSource
      val compiler = CompilerFactoryFactory.getDefaultCompilerFactory.newSimpleCompiler()
      compiler.cook(javaSource)

      val classLoader = compiler.getClassLoader
      val controllerClass = classLoader.loadClass(s"${packageName}.${className}")
      val main: Method = controllerClass.getMethod("main", classOf[Array[String]])
      context.actorOf(SimulationRunActor.props(webSocketOut, main))
  }
}
