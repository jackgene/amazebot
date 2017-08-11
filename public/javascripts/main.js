var roombaSimApp = angular.module(
  'roombaSimApp', ['ngCookies', 'ngWebSocket', 'ui.codemirror']
);
roombaSimApp.controller('roombaSimController', function ($scope, $cookies, $http, $websocket, $timeout) {
  var cellSize = 30, posLeftPx = 235, posTopPx = 235,
    move = {'horizontal': 0, 'vertical': -cellSize},
    dataStream;

  function processRobotInstructions(steps) {
    function turnRight() {
      var oldH = move.horizontal, oldV = move.vertical;

      move.horizontal = oldH != 0 ? 0 : -oldV;
      move.vertical = oldV != 0 ? 0 : oldH;
    }
    function moveForward() {
      posLeftPx += move.horizontal;
      posTopPx += move.vertical;

      $scope.pos.left = posLeftPx + 'px';
      $scope.pos.top = posTopPx + 'px';
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

  function establishWebsocketConnection() {
    dataStream = $websocket(
      location.protocol === 'https:' ? 'wss://' : 'ws://' + location.host + '/simulation'
    );
    dataStream.onMessage(function(message) {
      processRobotInstructions(JSON.parse(message.data));
    });
    dataStream.onClose(function() {
      dataStream = null;
    });
  }

  $scope.pos = {
    'left': posLeftPx + 'px',
    'top': posTopPx + 'px'
  };

  $scope.editorOptions = {
    lineWrapping : true,
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/x-java'
  };

  $scope.runSimulation = function() {
    if (!dataStream) establishWebsocketConnection();
    dataStream.send($scope.code);
  };


  $scope.code = $cookies.get('code');
  if (!$scope.code) {
    $http({
      method: 'GET',
      url: location.protocol + '//' + location.host + '/assets/java/TemplateRobot.java'
    }).
    then(
      function successCallback(response) {
        $scope.code = response.data;
      },
      function errorCallback(response) {
        console.log('Error obtaining template source:');
        console.log('status: ' + response.status);
        console.log('data: ' + response.data);
      }
    )
  }
});
