package models

import java.lang.reflect.Method
import java.security.{AccessController, PrivilegedAction}

import org.codehaus.janino.{ByteArrayClassLoader, SimpleCompiler}
import org.objectweb.asm._

/**
  * A supported language.
  */
object Language {
  def transform(byteCode: Array[Byte]): Array[Byte] = {
    val reader: ClassReader = new ClassReader(byteCode)
    val writer: ClassWriter = new ClassWriter(reader, 0)

    try {
      reader.accept(
        new ClassVisitor(Opcodes.ASM5, writer) {
          override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
            val mv: MethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

            new MethodVisitor(Opcodes.ASM5, mv) {
              override def visitLineNumber(line: Int, start: Label) {
                super.visitLineNumber(line, start)
                if (line > 0) {
                  mv.visitLdcInsn(line)
                  mv.visitMethodInsn(Opcodes.INVOKESTATIC, "actors/SimulationRunActor", "beforeRunningLine", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(classOf[Int])), false)
                }
              }
            }
          }
        },
        ClassReader.EXPAND_FRAMES
      )
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }

    writer.toByteArray
  }
}
sealed trait Language {
  def compileToEntryPointMethod(source: String): Method
}
case object Java extends Language {
//  import org.codehaus.commons.compiler.CompilerFactoryFactory

  private val PackageNameExtractor =
    """(?s).*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  def compileToEntryPointMethod(javaSource: String): Method = {
    System.setProperty("org.codehaus.janino.source_debugging.enable", "true")
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
