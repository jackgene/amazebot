// A random maze is generated each time you load the page.
//
// No methods are provided here, so you'll have to write
// your own. You are welcome to copy methods from earlier
// levels if you want.
//
// Can you write a program that will navigate the robot
// through ANY random maze?

package org.jointheleague.ecolban.cleverrobot;

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	private void setup() throws Exception {
		System.out.println("Running setup()");
		// Insert code that only runs once here
	}

	private boolean loop() throws Exception {
		System.out.println("Running loop()");
		// Insert repeating code here

		return true; // Return false to stop repeating
	}

	private void shutDown() throws Exception {
		reset();
		stop();
		closeConnection();
	}

	// DON'T CHANGE ANYTHING BELOW THIS LINE
	Sonar sonar = new Sonar();

	public CleverRobot(IRobotInterface iRobot) {
		super(iRobot);
	}

	public static void main(String[] args) throws Exception {
		IRobotInterface base = new SimpleIRobot();
		CleverRobot rob = new CleverRobot(base);
		rob.setup();
		while (rob.loop()) {
			Thread.sleep(500);
		}
		rob.shutDown();
	}
}
