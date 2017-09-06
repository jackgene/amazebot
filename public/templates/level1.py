# As before, you have to get the robot to the green circle.
# However, this time you have to avoid some walls.
#
# Write your code at he bottom of the file. You may use the
# "turn_left()", and "move_forward()" functions.

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot

robot = SimpleIRobot()


def move_forward():
	"""Move the robot forward approximately one 'square'"""
	# Move left and right wheels 500mm/s forward
	robot.driveDirect(500, 500)
	# Let the robot move for 1.975 seconds
	sleep(1.975)
	# Stop moving
	robot.stop()


def turn_left():
	"""Turns the robot left approximately 90 degrees."""
	# Move left wheel 400mm/s backwards
	# and right wheel 400mm/s forward
	robot.driveDirect(-400, 400)
	# Let the robot move for 0.436 seconds
	sleep(0.436)
	# Stop moving
	robot.stop()


move_forward()
# Your code here...
