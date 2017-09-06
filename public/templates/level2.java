// You have to navigate the robot through more challenging
// maze now.
//
// In addition, there is a "turnRight()" method available
// to you. However "turnRight()" simply calls "turnLeft()"
// three times. Can you make it better?

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
		// Let the robot move for 1975 milliseconds
		Thread.sleep(1975);
		// Stop moving
		stop();
	}

	/**
	 * Turns the robot approximately 90 degrees left.
	 */
	void turnLeft() throws Exception {
		// Move left wheel 400mm/s backwards
		// and right wheel 400mm/s forward
		driveDirect(-400, 400);
		// Let the robot move for 436 milliseconds
		Thread.sleep(436);
		// Stop moving
		stop();
	}

	/**
	 * Turns the robot approximately 90 degrees right.
	 */
	void turnRight() throws Exception {
		// Turn left three times
		turnLeft();
		turnLeft();
		turnLeft();
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
