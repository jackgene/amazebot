var roombaSimApp = angular.module(
  'roombaSimApp', ['ngCookies', 'ngWebSocket', 'ui.codemirror']
);
roombaSimApp.controller('roombaSimController', function ($scope, $cookies, $http, $websocket, $window, $timeout) {
  var robotRadiusMm = 173.5, pxPerMm = 0.1, dataStream;

  function processRobotInstruction(instruction) {
    switch (instruction.c) {
      case 'maze':
        $scope.finish.top = instruction.ft * pxPerMm - 4 + 'px';
        $scope.finish.left = instruction.fl * pxPerMm - 4 + 'px';
        $scope.finish.display = 'block';
        $scope.walls = instruction.w.map(function(wall) {
          return {
            top: wall.t * pxPerMm + 'px',
            left: wall.l * pxPerMm + 'px',
            height: wall.h * pxPerMm - 4 + 'px',
            width: wall.w * pxPerMm - 4 + 'px'
          };
        });
        break;

      case 'init':
        $scope.robot.display = 'none';
        $scope.robot.top = (instruction.t - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.left = (instruction.l - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.transform = 'rotate(' + instruction.o + 'rad)';
        $timeout(
          function() { $scope.robot.display = 'block' },
          100
        );
        break;

      case 'mv':
        $scope.robot.top = (instruction.t - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.left = (instruction.l - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.transform = 'rotate(' + instruction.o + 'rad)';
        break;

      case 'msg':
        $timeout(
          function() {$window.alert(instruction.m)},
          300
        );
        break;
    }
  }

  function establishWebsocketConnection() {
    dataStream = $websocket(
      (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/simulation'
    );
    dataStream.onMessage(function(message) {
      processRobotInstruction(JSON.parse(message.data));
    });
    dataStream.onClose(function() {
      dataStream = null;
    });
  }

  $scope.robot = {
    display: 'none'
  };
  $scope.finish = {
    display: 'none'
  };
  $scope.walls = [
  ];

  $scope.editorOptions = {
    lineWrapping: true,
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

  establishWebsocketConnection();
});
