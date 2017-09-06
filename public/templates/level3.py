# With two extra turns, the maze is a bit more challenging.
#
# Also, instead of "turning" left and right, you now "curve"
# left and right, allowing you to cut closer to the corners.

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
	# Turn left three times
	# Move robot 500mm/s forward,
	# with a radius of 833mm (1 square) to the right
	robot.drive(500, -833);
	# Let the robot move for 3 seconds
	sleep(3)
	# Stop moving
	robot.stop()


move_forward()
# Your code here...
