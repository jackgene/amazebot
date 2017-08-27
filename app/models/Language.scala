package models

import java.lang.reflect.Method

/**
  * A supported language.
  */
sealed trait Language {
  def compileToEntryPointMethod(source: String): Method
}
case object Java extends Language {
  import org.codehaus.commons.compiler.CompilerFactoryFactory

  private val PackageNameExtractor =
    """(?s).*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  def compileToEntryPointMethod(javaSource: String): Method = {
    val compiler = CompilerFactoryFactory.getDefaultCompilerFactory.newSimpleCompiler()
    compiler.cook(javaSource)
    val PackageNameExtractor(packageName: String) = javaSource
    val ClassNameExtractor(className: String) = javaSource

    compiler.getClassLoader.
      loadClass(s"${packageName}.${className}").
      getMethod("main", classOf[Array[String]])
  }
}
case object Python extends Language {
  import java.io.ByteArrayInputStream
  import org.python.core.{BytecodeLoader, imp => PythonCompiler}

  def compileToEntryPointMethod(source: String): Method = {
    val byteCode = PythonCompiler.compileSource(
      "sim", new ByteArrayInputStream(source.getBytes("UTF-8")), null
    )

    BytecodeLoader.
      makeClass("sim$py", byteCode).
      getMethod("main", classOf[Array[String]])
  }
}
