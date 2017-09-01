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
    val writer: ClassWriter = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS)

    try {
      reader.accept(
        new ClassVisitor(Opcodes.ASM5, writer) {
          override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
            val mv: MethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

            new MethodVisitor(Opcodes.ASM5, mv) {
              var lastLineVisited: Int = 0
              var instrumentedLines: Set[Int] = Set()

              private def instrumentIfNecessary(): Unit = {
                if (lastLineVisited > 0) {
                  mv.visitLdcInsn(lastLineVisited)
                  mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "actors/SimulationRunActor",
                    "beforeRunningLine",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(classOf[Int])),
                    false
                  )
                  instrumentedLines = instrumentedLines + lastLineVisited
                  lastLineVisited = 0
                }
              }

              override def visitLineNumber(line: Int, start: Label) {
                super.visitLineNumber(line, start)
                if (line > 0 && !instrumentedLines.contains(line)) lastLineVisited = line
              }

              override def visitInsn(opcode: Int) {
                if (opcode != Opcodes.RETURN) instrumentIfNecessary()
                super.visitInsn(opcode)
              }

              override def visitIntInsn(opcode: Int, operand: Int) {
                instrumentIfNecessary()
                super.visitIntInsn(opcode, operand)
              }

              override def visitVarInsn(opcode: Int, `var`: Int) {
                instrumentIfNecessary()
                super.visitVarInsn(opcode, `var`)
              }

              override def visitTypeInsn(opcode: Int, `type`: String) {
                if (opcode == Opcodes.NEW) {
                  super.visitTypeInsn(opcode, `type`)
                  instrumentIfNecessary()
                }
                else {
                  instrumentIfNecessary()
                  super.visitTypeInsn(opcode, `type`)
                }
              }

              override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
                instrumentIfNecessary()
                super.visitFieldInsn(opcode, owner, name, desc)
              }

              override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
                instrumentIfNecessary()
                super.visitMethodInsn(opcode, owner, name, desc, itf)
              }

              override def visitInvokeDynamicInsn(name: String, desc: String, bsm: Handle, bsmArgs: Object*) {
                instrumentIfNecessary()
                super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs: _*)
              }

              override def visitJumpInsn(opcode: Int, label: Label) {
                instrumentIfNecessary()
                super.visitJumpInsn(opcode, label)
              }

              override def visitLdcInsn(cst: Any) {
                instrumentIfNecessary()
                super.visitLdcInsn(cst)
              }

              override def visitIincInsn(`var`: Int, increment: Int) {
                instrumentIfNecessary()
                super.visitIincInsn(`var`, increment)
              }

              override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*) {
                instrumentIfNecessary()
                super.visitTableSwitchInsn(min, max, dflt, labels: _*)
              }

              override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]) {
                instrumentIfNecessary()
                super.visitLookupSwitchInsn(dflt, keys, labels)
              }

              override def visitMultiANewArrayInsn(desc: String, dims: Int) {
                instrumentIfNecessary()
                super.visitMultiANewArrayInsn(desc, dims)
              }

              override def visitInsnAnnotation(typeRef: Int, typePath: TypePath, desc: String, visible: Boolean): AnnotationVisitor = {
                instrumentIfNecessary()
                super.visitInsnAnnotation(typeRef, typePath, desc, visible)
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
  private val PackageNameExtractor =
    """(?s).*package\s+((?:[A-Za-z][A-Za-z0-9]+)(?:\.[A-Za-z][A-Za-z0-9]+)*).*""".r
  private val ClassNameExtractor =
    """(?s).*public\s+class\s+([A-Za-z][A-Za-z0-9]+).*""".r

  System.setProperty("org.codehaus.janino.source_debugging.enable", "true")

  def compileToEntryPointMethod(javaSource: String): Method = {
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

    try {
      compiler.getClassLoader.
        loadClass(s"${packageName}${className}").
        getMethod("main", classOf[Array[String]])
    } catch {
      case e: VerifyError =>
        throw new RuntimeException(e.getMessage)
    }
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
