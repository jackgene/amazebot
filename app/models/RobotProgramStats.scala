package models

import java.lang.management.ManagementFactory

/**
  * Details about the robot program.
  *
  * @author Jack Leow
  * @since September 2017
  */
case class RobotProgramStats(
  runningThread: Thread,
  startTimeMillis: Long = System.currentTimeMillis
) {
  def runningTimeMillis: Long = System.currentTimeMillis - startTimeMillis

  def cpuTimePercent: Double =
    ManagementFactory.getThreadMXBean.getThreadCpuTime(runningThread.getId) / 1000000.0 / runningTimeMillis
}
