from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot, Sonar

robot = SimpleIRobot()
sonar = Sonar()

def setup():
	# Initialization code here
	pass


def loop():
	# Repeating code here
	return True


def shutdown():
	robot.reset()
	robot.stop()
	robot.closeConnection()


setup()
while loop():
	pass
shutdown()