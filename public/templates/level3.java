// This is just the same maze as the last level with two
// extra turns.
//
// However, the "turnLeft()" and "turnRight()" methods have
// been removed.
//
// Instead, you have the following:
// - curveLeft()
// - curveRight()
//
// Which moves the robot in a curve instead of turning
// in-place. It uses the robot's "drive" method, instead of
// "driveDirect".
//
// Try and understand how the curve methods works.

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	void go() throws Exception {
		moveForward();
		// Your code here...

	}

	/**
	 * Move the robot forward approximately one "square".
	 */
	void moveForward() throws Exception {
		// Move left and right wheels 500mm/s forward
		driveDirect(500, 500);
		// Let the robot move for 1642 milliseconds
		Thread.sleep(1642);
		// Stop moving
		stop();
	}

	/**
	 * Move the robot approximately 90 degrees left in a curve.
	 */
	void curveLeft() throws Exception {
		// Move robot 500mm/s forward,
		// with a radius of 833mm (1 square) to the left
		drive(500, 833);
		// Let the robot move for 3000 milliseconds
		Thread.sleep(3000);
		// Stop moving
		stop();
	}

	/**
	 * Move the robot approximately 90 degrees right in a curve.
	 */
	void curveRight() throws Exception {
		// Move robot 500mm/s forward,
		// with a radius of 833mm (1 square) to the right
		drive(500, -833);
		// Let the robot move for 3000 milliseconds
		Thread.sleep(3000);
		// Stop moving
		stop();
	}

	// DON'T CHANGE ANYTHING BELOW THIS LINE
	CleverRobot(IRobotInterface iRobot) throws Exception {
		super(iRobot);
		go();
	}

	public static void main(String[] args) throws Exception {
		CleverRobot rob = new CleverRobot(new SimpleIRobot());
	}
}
