# The maze is much more complex in this level.
#
# To help you navigate this map, you have been given a new
# tool - the sonar.
#
# The sonar detects how far walls are from the robot.
#
# There are sonars in three directions:
# - left
# - right
# - center (front)
#
# A new method has been introduced that uses sonar:
# - move_forward_to_wall()
#
# Look at the "move_forward_to_wall()" function and try and
# understand how the sonar works.

from time import sleep
from org.jointheleague.ecolban.rpirobot import SimpleIRobot, Sonar

robot = SimpleIRobot()
sonar = Sonar()


def move_forward():
	"""Move the robot forward approximately one square"""
	# Move left and right wheels 500mm/s forward
	robot.driveDirect(500, 500)
	# Let the robot move for 1.642 seconds
	sleep(1.642)
	# Stop moving
	robot.stop()


def move_forward_to_wall():
	"""Move the robot forward until it is one square away
		from the next wall."""
	# Read front sonar distance (in centimeters)
	distance_remaining = sonar.readSonar("center")
	# More than one square from the next wall...
	while distance_remaining > 66:
		if distance_remaining > 108:
			# Go fast and far if wall is far away
			robot.driveDirect(500, 500)
			sleep(1)
		elif distance_remaining > 72:
			# Don't go as far as wall gets closer
			robot.driveDirect(500, 500)
			sleep(0.1)
		else:
			# Slow down when wall is really close
			robot.driveDirect(200, 200)
			sleep(0.03)
		# Read front sonar distance again
		distance_remaining = sonar.readSonar("center");
	robot.stop()


def curve_left():
	"""Move the robot approximately 90 degrees left in a curve."""
	# Move robot 500mm/s forward,
	# with a radius of 417mm (1/2 square) to the left
	robot.drive(500, 417);
	# Let the robot move for 1.65 seconds
	sleep(1.65)
	# Stop moving
	robot.stop()


def curve_right():
	"""Move the robot approximately 90 degrees right in a curve."""
	# Move robot 500mm/s forward,
	# with a radius of 417mm (1/2 square) to the right
	robot.drive(500, -417);
	# Let the robot move for 1.65 seconds
	sleep(1.65)
	# Stop moving
	robot.stop()


move_forward_to_wall()
# Your code here...
