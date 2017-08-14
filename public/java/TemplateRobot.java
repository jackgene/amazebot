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
		driveDirect(0, 0);
	}

	private boolean loop() throws Exception {
		driveDirect(60, 60);
		Thread.sleep(975);
		driveDirect(100, -100);
		Thread.sleep(1820);

		return --loopsRemaining > 0;
	}

	private void shutDown() throws IOException {
		reset();
		stop();
		closeConnection();
	}
}
