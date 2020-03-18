package engines

import org.objectweb.asm._

import scala.util.Try

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

              override def visitLineNumber(line: Int, start: Label): Unit = {
                super.visitLineNumber(line, start)
                if (line > 0 && !instrumentedLines.contains(line)) lastLineVisited = line
              }

              override def visitInsn(opcode: Int): Unit = {
                if (opcode != Opcodes.RETURN) instrumentIfNecessary()
                super.visitInsn(opcode)
              }

              override def visitIntInsn(opcode: Int, operand: Int): Unit = {
                instrumentIfNecessary()
                super.visitIntInsn(opcode, operand)
              }

              override def visitVarInsn(opcode: Int, `var`: Int): Unit = {
                instrumentIfNecessary()
                super.visitVarInsn(opcode, `var`)
              }

              override def visitTypeInsn(opcode: Int, `type`: String): Unit = {
                if (opcode == Opcodes.NEW) {
                  super.visitTypeInsn(opcode, `type`)
                  instrumentIfNecessary()
                }
                else {
                  instrumentIfNecessary()
                  super.visitTypeInsn(opcode, `type`)
                }
              }

              override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String): Unit = {
                instrumentIfNecessary()
                super.visitFieldInsn(opcode, owner, name, desc)
              }

              override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean): Unit = {
                instrumentIfNecessary()
                super.visitMethodInsn(opcode, owner, name, desc, itf)
              }

              override def visitInvokeDynamicInsn(name: String, desc: String, bsm: Handle, bsmArgs: Object*): Unit = {
                instrumentIfNecessary()
                super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs: _*)
              }

              override def visitJumpInsn(opcode: Int, label: Label): Unit = {
                instrumentIfNecessary()
                super.visitJumpInsn(opcode, label)
              }

              override def visitLdcInsn(cst: Any): Unit = {
                instrumentIfNecessary()
                super.visitLdcInsn(cst)
              }

              override def visitIincInsn(`var`: Int, increment: Int): Unit = {
                instrumentIfNecessary()
                super.visitIincInsn(`var`, increment)
              }

              override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*): Unit = {
                instrumentIfNecessary()
                super.visitTableSwitchInsn(min, max, dflt, labels: _*)
              }

              override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]): Unit = {
                instrumentIfNecessary()
                super.visitLookupSwitchInsn(dflt, keys, labels)
              }

              override def visitMultiANewArrayInsn(desc: String, dims: Int): Unit = {
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
trait Language {
  def makeRobotControlScript(source: String): () => Try[Unit]
}
