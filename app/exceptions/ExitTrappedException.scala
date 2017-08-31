package exceptions

/**
  * Indicates an attempt to call System.exit(...).
  *
  * @author Jack Leow
  * @since August 2017
  */
case class ExitTrappedException(status: Int) extends SecurityException
