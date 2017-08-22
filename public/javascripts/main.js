var roombaSimApp = angular.module(
  'roombaSimApp', ['ngCookies', 'ngWebSocket', 'ui.codemirror']
);
roombaSimApp.controller('roombaSimController', function ($scope, $cookies, $http, $websocket, $window, $timeout, $log) {
  var robotRadiusMm = 173.5, pxPerMm = 0.1, dataStream;

  function processRobotInstruction(instruction) {
    switch (instruction.c) {
      case 'maze':
        $scope.finish.top = instruction.ft * pxPerMm - 25 + 'px';
        $scope.finish.left = instruction.fl * pxPerMm - 25 + 'px';
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
        $timeout(
          function() {
            $scope.robot.display = 'block';
            $scope.robot.transform = 'rotate(' + (instruction.o - Math.PI) + 'rad)';
            $timeout(
              function() { $scope.robot.transform = 'rotate(' + instruction.o + 'rad)' },
              0
            )
          },
          0
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

      case 'log':
        if (instruction.m != "" && instruction.m != "\n")
          $scope.console.push(
            {
              type: instruction.t == 'e' ? 'stderr' : 'stdout',
              text: instruction.m
            }
          );
        $timeout(function() {
          var console = document.getElementById("console");
          console.scrollTop = console.scrollHeight;
        }, 0, false);
        break;
    }
  }

  function establishWebsocketConnection() {
    dataStream = $websocket(
      (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + location.pathname + '/simulation'
    );
    dataStream.onMessage(function(message) {
      $log.log(message.data);
      processRobotInstruction(JSON.parse(message.data));
    });
    dataStream.onClose(function() {
      dataStream = null;
    });
  }

  $scope.console = [];
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
    indentUnit: 4,
    indentWithTabs: true,
    mode: 'text/x-java'
  };

  $scope.runSimulation = function() {
    var cookieExpires = new Date(new Date().setFullYear(new Date().getFullYear() + 1));

    if (!dataStream) establishWebsocketConnection();
    dataStream.send($scope.code);
    $cookies.put('lastAttempted', location.pathname, {expires: cookieExpires, path: '/'});
    $cookies.put('code', $scope.code, {expires: cookieExpires, path: location.pathname});
  };


  $scope.code = $cookies.get('code');
  if (!$scope.code) {
    $http({
      method: 'GET',
      url: location.protocol + '//' + location.host + location.pathname + '/template.java'
    }).
    then(
      function successCallback(response) {
        $scope.code = response.data;
      },
      function errorCallback(response) {
        $log.log('Error obtaining template source:');
        $log.log('status: ' + response.status);
        $log.log('data: ' + response.data);
      }
    )
  }

  establishWebsocketConnection();
});
