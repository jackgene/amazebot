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
		while (rob.loop()) {
		}
		rob.shutDown();
	}

	private void setup() throws Exception {
		driveDirect(500, 500);
		Thread.sleep(3975);
		driveDirect(100, -100);
		Thread.sleep(1820);
	}

	private boolean loop() throws Exception {
		drive(500, -2000);
		Thread.sleep(5000);

		return true;
	}

	private void shutDown() throws IOException {
		reset();
		stop();
		closeConnection();
	}
}
