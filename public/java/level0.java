// A previous programmer had made this Roomba too clever
// and now, all it does is go round and round and round
// the goal.
// Can you help the robot find the goal?

package org.jointheleague.ecolban.cleverrobot;

import java.io.IOException;
import java.util.Random;

import org.jointheleague.ecolban.rpirobot.IRobotAdapter;
import org.jointheleague.ecolban.rpirobot.IRobotInterface;
import org.jointheleague.ecolban.rpirobot.SimpleIRobot;

public class CleverRobot extends IRobotAdapter {
	private boolean tailLight;
	private int loopsRemaining = 3;

	public CleverRobot(IRobotInterface iRobot) {
		super(iRobot);
	}

	public static void main(String[] args) throws Exception {
		IRobotInterface base = new SimpleIRobot();
		CleverRobot rob = new CleverRobot(base);
		rob.setup();
	}

	private void setup() throws Exception {
		driveDirect(500, 500);
		Thread.sleep(1975);
		driveDirect(-100, 100);
		Thread.sleep(1820);
		drive(500, -1000);
	}
}
