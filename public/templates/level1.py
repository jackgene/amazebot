# As before, you have to get the robot to the green circle.
# However, you will have to navigate the robot around some
# walls now.
#
# Two functions have been provided for you:
# - turn_left()
# - move_forward()
#
# To use (or call) a function, just type the function name
# followed by "()". You can see an example on line 43.
#
# Try to understand how the provided functions work.
#
# Write your code at he bottom of the file.

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


move_forward()
# Your code here...
