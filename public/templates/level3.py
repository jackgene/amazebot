# This is just the same maze as the last level with two
# extra turns.
#
# However, the "turn_left()" and "turn_right()" functions
# have been removed.
#
# Instead, you have the following:
# - curve_left()
# - curve_right()
#
# Which moves the robot in a curve instead of turning
# in-place. It uses the robot's "drive" method, instead of
# "driveDirect".
#
# Try and understand how the curve functions works.

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot

robot = SimpleIRobot()


def move_forward():
	"""Move the robot forward approximately one square"""
	# Move left and right wheels 500mm/s forward
	robot.driveDirect(500, 500)
	# Let the robot move for 1.642 seconds
	sleep(1.642)
	# Stop moving
	robot.stop()


def curve_left():
	"""Move the robot approximately 90 degrees left in a curve."""
	# Move robot 500mm/s forward,
	# with a radius of 833mm (1 square) to the left
	robot.drive(500, 833);
	# Let the robot move for 3 seconds
	sleep(3)
	# Stop moving
	robot.stop()


def curve_right():
	"""Move the robot approximately 90 degrees right in a curve."""
	# Move robot 500mm/s forward,
	# with a radius of 833mm (1 square) to the right
	robot.drive(500, -833);
	# Let the robot move for 3 seconds
	sleep(3)
	# Stop moving
	robot.stop()


move_forward()
# Your code here...
