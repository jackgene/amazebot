// A previous programmer had made this Roomba too clever
// and now, all it does is go round and round and round
// the green circle.
// Can you help the robot get to the green circle?

package org.jointheleague.ecolban.cleverrobot;

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	public CleverRobot(IRobotInterface iRobot) throws Exception {
		super(iRobot);
		driveDirect(500, 500);
		Thread.sleep(1975);
		driveDirect(-100, 100);
		Thread.sleep(1820);
		drive(500, -1000);
	}

	public static void main(String[] args) throws Exception {
		CleverRobot rob = new CleverRobot(new SimpleIRobot());
	}
}
