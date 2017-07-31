var roombaSimApp = angular.module('roombaSimApp', ['ui.codemirror']);

roombaSimApp.controller('roombaSimController', function ($scope, $http, $timeout) {
  var cellSize = 30, move = {"horizontal": 0, "vertical": -cellSize};

  function processRobotInstructions(steps) {
    function turnRight() {
      var oldH = move.horizontal, oldV = move.vertical;

      move.horizontal = oldH != 0 ? 0 : -oldV;
      move.vertical = oldV != 0 ? 0 : oldH;
      console.log("move.h: " + move.horizontal);
      console.log("move.v: " + move.vertical);
    }
    function moveForward() {
      $scope.pos.leftPx = $scope.pos.leftPx + move.horizontal;
      $scope.pos.topPx = $scope.pos.topPx + move.vertical;

      $scope.pos.left = $scope.pos.leftPx + 'px';
      $scope.pos.top = $scope.pos.topPx + 'px';
      console.log("left: " + $scope.pos.left);
      console.log("top: " + $scope.pos.top);
    }

    switch(steps[0]) {
      case 'r':
        turnRight();
        processRobotInstructions(steps.slice(1));
        break;

      case 'f':
        moveForward();
        $timeout(function() { processRobotInstructions(steps.slice(1)) }, 200);
        break;
    }
  }

  $scope.pos = {
    "leftPx": 235,
    "topPx": 235,
    "left": "235px",
    "top": "235px"
  };

  $scope.editorOptions = {
    lineWrapping : true,
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/x-java'
  };
  $scope.code =
    '// Control the red box using the "robot" object.\n' +
    '// Robot supports the following operations:\n' +
    '// - moveForward() - one step forward\n' +
    '// - turnRight() - turn 90 degrees right\n' +
    'for (int i = 0; i < 4; ++ i) {\n' +
    '  robot.moveForward();\n' +
    '  robot.moveForward();\n' +
    '  robot.turnRight();\n' +
    '}';


  $scope.runSimulation = function() {
    console.log("POST: " + location.protocol + '//' + location.host + "/run-simulation.js");
    console.log("POST data: " + $scope.code);
    $http({
      method: 'POST',
      url: location.protocol + '//' + location.host + "/run-simulation.js",
      headers: {
        'Content-Type': undefined
      },
      data: $scope.code
    }).then(
      function successCallback(response) {
        console.log('Received: ' + response.data);
        processRobotInstructions(response.data);
      },
      function errorCallback(response) {
        console.log('Error running simulation:');
        console.log('status: ' + response.status);
        console.log('data: ' + response.data);
      }
    );
  };
});
