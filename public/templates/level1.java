// As before, you have to get the robot to the green circle.
// However, this time you have to avoid some walls.
//
// Write your code in the "go()" method. You may use the
// "turnLeft()", and "moveForward()" methods.

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
	 * Turns the robot left approximately 90 degrees.
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

	// DON'T CHANGE ANYTHING BELOW THIS LINE
	CleverRobot(IRobotInterface iRobot) throws Exception {
		super(iRobot);
		go();
	}

	public static void main(String[] args) throws Exception {
		CleverRobot rob = new CleverRobot(new SimpleIRobot());
	}
}
