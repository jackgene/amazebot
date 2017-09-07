// You have to navigate the robot through more challenging
// maze in this level.
//
// Also, an additional method is available to you:
// - turnRight()
//
// However "turnRight()" just calls "turnLeft()" 3 times.
// Can you make a better "turnRight()"?

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
