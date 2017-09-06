// A previous programmer had made this robot too clever
// and now, all it does is go round and round and round
// the green circle.
// Can you help the robot get to the green circle?

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	CleverRobot(IRobotInterface iRobot) throws Exception {
		super(iRobot);

		// Move forward
		driveDirect(500, 500);
		Thread.sleep(1975);

		// Turn left
		driveDirect(-100, 100);
		Thread.sleep(1820);

		// Move in a circle
		drive(500, -1000);
	}

	public static void main(String[] args) throws Exception {
		CleverRobot rob = new CleverRobot(new SimpleIRobot());
	}
}
