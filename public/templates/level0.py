# A previous programmer had made this Roomba too clever
# and now, all it does is go round and round and round
# the green circle.
# Can you help the robot get to the green circle?

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot

robot = SimpleIRobot()
robot.driveDirect(500, 500)
sleep(1.975)
robot.driveDirect(-100, 100)
sleep(1.820)
robot.drive(500, -1000)
