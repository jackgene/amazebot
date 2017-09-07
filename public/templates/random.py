# A random maze is generated each time you load the page.
#
# No functions are provided here, so you'll have to write
# your own. You are welcome to copy functions from earlier
# levels if you want.
#
# Can you write a program that will navigate the robot
# through ANY random maze?

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot, Sonar

robot = SimpleIRobot()
sonar = Sonar()


def setup():
	print "Running setup()"
	# Insert code that only runs once here


def loop():
	print "Running loop()"
	# Insert repeating code here

	return True  # Return False to stop repeating


def shutdown():
	robot.reset()
	robot.stop()
	robot.closeConnection()


setup()
while loop():
	sleep(0.5)
shutdown()
