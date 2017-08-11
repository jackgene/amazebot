package org.jointheleague.ecolban.cleverrobot;

import java.io.IOException;

import org.jointheleague.ecolban.rpirobot.IRobotAdapter;
import org.jointheleague.ecolban.rpirobot.IRobotInterface;
import org.jointheleague.ecolban.rpirobot.SimpleIRobot;

public class CleverRobot extends IRobotAdapter {
	private boolean tailLight;

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
		driveDirect(0, 0);
	}

	private boolean loop() throws Exception {
		driveDirect(1, 1);
		Thread.sleep(500);
		driveDirect(1, 1);
		Thread.sleep(500);
		driveDirect(1, -1);
		Thread.sleep(500);

		return true;
	}

	private void shutDown() throws IOException {
		reset();
		stop();
		closeConnection();
	}
}
