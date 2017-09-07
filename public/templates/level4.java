// The maze is much more complex in this level.
//
// To help you navigate this map, you have been given a new
// tool - the sonar.
//
// The sonar detects how far walls are from the robot.
//
// There are sonars in three directions:
// - left
// - right
// - center (front)
//
// A new method has been introduced that uses sonar:
// - moveRobotToWall()
//
// Look at the "moveForwardToWall()" method and try and
// understand how the sonar works.

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	void go() throws Exception {
		moveForwardToWall();
		// Your code here...

	}

	/**
	 * Move the robot forward approximately one square.
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
	 * Move the robot forward until it is one square away
     * from the next wall.
	 */
	void moveForwardToWall() throws Exception {
		// Read front sonar distance (in centimeters)
		int distanceRemaining = sonar.readSonar("center");
		// More than one square from the next wall...
		while (distanceRemaining > 66) {
			if (distanceRemaining > 108) {
				// Go fast and far if wall is far away
				driveDirect(500, 500);
				Thread.sleep(1000);
			} else if (distanceRemaining > 72) {
				// Don't go as far as wall gets closer
				driveDirect(500, 500);
				Thread.sleep(100);
			} else {
				// Slow down when wall is really close
				driveDirect(200, 200);
				Thread.sleep(30);
			}
			// Read front sonar distance again
			distanceRemaining = sonar.readSonar("center");
		}
		stop();
	}

	/**
	 * Move the robot approximately 90 degrees left in a curve.
	 */
	void curveLeft() throws Exception {
		// Move robot 500mm/s forward,
		// with a radius of 417mm (1/2 square) to the left
		drive(500, 417);
		// Let the robot move for 1650 milliseconds
		Thread.sleep(1650);
		// Stop moving
		stop();
	}

	/**
	 * Move the robot approximately 90 degrees right in a curve.
	 */
	void curveRight() throws Exception {
		// Move robot 500mm/s forward,
		// with a radius of 417mm (1/2 square) to the right
		drive(500, -417);
		// Let the robot move for 1650 milliseconds
		Thread.sleep(1650);
		// Stop moving
		stop();
	}

	// DON'T CHANGE ANYTHING BELOW THIS LINE
	Sonar sonar = new Sonar();

	CleverRobot(IRobotInterface iRobot) throws Exception {
		super(iRobot);
		go();
	}

	public static void main(String[] args) throws Exception {
		CleverRobot rob = new CleverRobot(new SimpleIRobot());
	}
}
