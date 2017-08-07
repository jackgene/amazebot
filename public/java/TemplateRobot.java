import models.Robot;

public class CleverRobot {
  public static void run(Robot robot) {
    // Control the red box using the "robot" object.
    // Robot supports the following operations:
    // - moveForward() - one step forward
    // - turnRight() - turn 90 degrees right
    for (int i = 0; i < 4; ++ i) {
      robot.moveForward();
      robot.moveForward();
      robot.turnRight();
    }
  }
}