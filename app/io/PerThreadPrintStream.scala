package io

import java.io.{OutputStream, PrintStream}
import java.util.Locale

private object NoopOutputStream extends OutputStream {
  override def write(b: Int): Unit = { /* noop */ }
}
object PerThreadPrintStream {
  private val origStdOut: PrintStream = System.out
  private val stdOutHolder = new ThreadLocal[PrintStream] {
    override val initialValue = origStdOut
  }
  val out: PrintStream = new PerThreadPrintStream(stdOutHolder)
  def redirectStdOut(dest: PrintStream): Unit = {
    stdOutHolder.set(dest)
  }

  private val origStdErr: PrintStream = System.err
  private val stdErrHolder = new ThreadLocal[PrintStream] {
    override val initialValue = origStdErr
  }
  val err: PrintStream = new PerThreadPrintStream(stdErrHolder)
  def redirectStdErr(dest: PrintStream): Unit = {
    stdErrHolder.set(dest)
  }
}
class PerThreadPrintStream(delegateHolder: ThreadLocal[PrintStream]) extends PrintStream(NoopOutputStream) {
  def delegate: PrintStream = delegateHolder.get()

  override def print(c: Char): Unit = delegate.print(c)

  override def println(x: Long): Unit = delegate.println(x)

  override def printf(format: String, args: AnyRef*): PrintStream = delegate.printf(format, args)

  override def println(x: Int): Unit = delegate.println(x)

  override def write(b: Int): Unit = delegate.write(b)

  override def print(s: String): Unit = delegate.print(s)

  override def print(i: Int): Unit = delegate.print(i)

  override def print(obj: scala.Any): Unit = delegate.print(obj)

  override def print(s: Array[Char]): Unit = delegate.print(s)

  override def format(l: Locale, format: String, args: AnyRef*): PrintStream = delegate.format(l, format, args)

  override def checkError(): Boolean = delegate.checkError()

  override def println(x: String): Unit = delegate.println(x)

  override def println(x: Boolean): Unit = delegate.println(x)

  override def append(csq: CharSequence, start: Int, end: Int): PrintStream = delegate.append(csq, start, end)

  override def println(x: Array[Char]): Unit = delegate.println(x)

  override def write(buf: Array[Byte], off: Int, len: Int): Unit = delegate.write(buf, off, len)

  override def print(l: Long): Unit = delegate.print(l)

  override def append(csq: CharSequence): PrintStream = delegate.append(csq)

  override def println(x: Double): Unit = delegate.println(x)

  override def print(f: Float): Unit = delegate.print(f)

  override def print(b: Boolean): Unit = delegate.print(b)

  override def println(): Unit = delegate.println()

  override def println(x: scala.Any): Unit = delegate.println(x)

  override def printf(l: Locale, format: String, args: AnyRef*): PrintStream = delegate.printf(l, format, args)

  override def format(format: String, args: AnyRef*): PrintStream = delegate.format(format, args)

  override def println(x: Char): Unit = delegate.println(x)

  override def println(x: Float): Unit = delegate.println(x)

  override def print(d: Double): Unit = delegate.print(d)

  override def flush(): Unit = delegate.flush()

  override def close(): Unit = delegate.close()

  override def append(c: Char): PrintStream = delegate.append(c)

  override def write(b: Array[Byte]): Unit = delegate.write(b)
}
