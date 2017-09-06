# You have to navigate the robot through more challenging
# maze now.
#
# In addition, there is a "turn_right()" function available
# to you. However "turn_right()" simply calls "turn_left()"
# three times. Can you make it better?

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
	"""Turns the robot approximately 90 degrees left."""
	# Move left wheel 400mm/s backwards
	# and right wheel 400mm/s forward
	robot.driveDirect(-400, 400)
	# Let the robot move for 0.436 seconds
	sleep(0.436)
	# Stop moving
	robot.stop()


def turn_right():
	"""Turns the robot approximately 90 degrees right."""
	# Turn left three times
	turn_left()
	turn_left()
	turn_left()


move_forward()
# Your code here...
