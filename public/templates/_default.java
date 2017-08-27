package org.jointheleague.ecolban.cleverrobot;

import org.jointheleague.ecolban.rpirobot.*;

public class CleverRobot extends IRobotAdapter {
	Sonar sonar = new Sonar();

	public CleverRobot(IRobotInterface iRobot) {
		super(iRobot);
	}

	public static void main(String[] args) throws Exception {
		IRobotInterface base = new SimpleIRobot();
		CleverRobot rob = new CleverRobot(base);
		rob.setup();
		while (rob.loop()) {
		}
		rob.shutDown();
	}

	private void setup() throws Exception {
	}

	private boolean loop() throws Exception {
		Thread.sleep(1000);

		return true;
	}

	private void shutDown() throws Exception {
		reset();
		stop();
		closeConnection();
	}
}
