package engines

import java.lang.reflect.{InvocationTargetException, Method}
import java.security.{AccessController, PrivilegedAction}

import exceptions.ExitTrappedException
import org.codehaus.janino.{ByteArrayClassLoader, SimpleCompiler}
import play.api.Logger

import scala.util.Try

/**
  * Support for running Java robot control programs.
  */
case object Java extends Language {
  private val PackageNameExtractor =
    """(?s).*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  System.setProperty("org.codehaus.janino.source_debugging.enable", "true")

  override def makeRobotControlScript(javaSource: String): () => Try[Unit] = {
    Logger.info("Compiling Java source to byte code")
    val compiler = new SimpleCompiler() {
      var result: ClassLoader = _

      override def cook(classes: java.util.Map[String,Array[Byte]]): Unit = {
        import scala.collection.JavaConverters._
        val transformedClasses: java.util.Map[String,Array[Byte]] =
          classes.asScala.mapValues { bytes: Array[Byte] =>
            Language.transform(bytes)
          }.
          asJava
        result = AccessController.doPrivileged(
          new PrivilegedAction[ClassLoader]() {
            def run: ClassLoader =
              new ByteArrayClassLoader(
                transformedClasses,
                Thread.currentThread.getContextClassLoader
              )
          }
        )

        super.cook(transformedClasses)
      }

      override def getClassLoader: ClassLoader = {
        if (result == null) throw new IllegalStateException("""Must only be called after "cook()"""")
        result
      }
    }
    compiler.cook(javaSource)
    val packageName = javaSource match {
      case PackageNameExtractor(packageName: String) => packageName + "."
      case _ => ""
    }
    val ClassNameExtractor(className: String) = javaSource

    val main: Method =
      try {
        compiler.getClassLoader.
          loadClass(s"${packageName}${className}").
          getMethod("main", classOf[Array[String]])
      } catch {
        case e: VerifyError =>
          throw new RuntimeException(e.getMessage)
      }

    () => Try[Unit] {
      main.invoke(null, Array[String]())
    }.recover {
      case e: InvocationTargetException => e.getCause match {
        case cause: Throwable => throw cause
      }
    }
  }
}
