# You have to navigate the robot through more challenging
# maze in this level.
#
# Also, an additional function is available to you:
# - turn_right()
#
# However "turn_right()" just calls "turn_left()" 3 times.
# Can you make a better "turn_right()"?

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot

robot = SimpleIRobot()


def move_forward():
	"""Move the robot forward approximately one square"""
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
