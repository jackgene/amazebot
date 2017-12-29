var aMazeBotApp = angular.module(
  'aMazeBotApp', ['ngCookies', 'ngWebSocket', 'ui.codemirror']
);
aMazeBotApp.controller('aMazeBotController', function ($scope, $cookies, $http, $websocket, $window, $timeout, $interval, $log) {
  var robotRadiusMm = 173.5, pxPerMm = 0.1, dataStream, lastExecutedLine, startTimeMillis = 0, timerPromise = null;

  function saveSessionState() {
    var cookieExpires = new Date(new Date().setFullYear(new Date().getFullYear() + 1));

    $cookies.put('lang', $scope.lang, {expires: cookieExpires, path: '/'});
    $cookies.put('lastAttempted', location.pathname, {expires: cookieExpires, path: '/'});
    $cookies.put('source.' + $scope.lang, $scope.source, {expires: cookieExpires, path: location.pathname});
  }

  function initializeSource() {
    $scope.source = $cookies.get('source.' + $scope.lang);
    if (!$scope.source) loadSourceFromTemplate();
    switch ($scope.lang) {
      case 'java':
        $scope.codemirrorEditor.setOption('mode', 'text/x-java');
        break;

      case 'py':
        $scope.codemirrorEditor.setOption('mode', 'text/x-python');
        break;
    }
  }

  function loadSourceFromTemplate() {
    $http({
      method: 'GET',
      url: location.protocol + '//' + location.host + location.pathname + '/template.' + $scope.lang
    }).
    then(
      function successCallback(response) {
        $scope.source = response.data;
        saveSessionState();
      },
      function errorCallback(response) {
        $log.log('Error obtaining template source:');
        $log.log('status: ' + response.status);
        $log.log('data: ' + response.data);
      }
    )
  }

  function stopTimer() {
    if (timerPromise !== null) {
      $interval.cancel(timerPromise);
      timerPromise = null;
    }
  }

  function processRobotInstruction(instruction) {
    switch (instruction.c) {
      case 'maze':
        $scope.finish.top = instruction.ft * pxPerMm - 25 + 'px';
        $scope.finish.left = instruction.fl * pxPerMm - 25 + 'px';
        $scope.finish.display = 'block';
        (function initializeWalls(wallsHistory) {
          if (wallsHistory.length > 0) {
            $scope.walls = wallsHistory[0].map(function(wall) {
              return {
                top: wall.t * pxPerMm + 'px',
                left: wall.l * pxPerMm + 'px',
                height: wall.h * pxPerMm - 4 + 'px',
                width: wall.w * pxPerMm - 4 + 'px'
              };
            });
            $timeout(function() { initializeWalls(wallsHistory.slice(1)) }, 50);
          }
        })(instruction.w.reverse());
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
          10
        );
        break;

      case 'm':
        $scope.robot.top = (instruction.t - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.left = (instruction.l - robotRadiusMm) * pxPerMm + 'px';
        $scope.robot.transform = 'rotate(' + instruction.o + 'rad)';
        break;

      case 'l':
        if (lastExecutedLine) {
          $scope.codemirrorEditor.getDoc().removeLineClass(lastExecutedLine, 'wrap', 'running');
        }
        if (instruction.l > 0) {
          lastExecutedLine = $scope.codemirrorEditor.getDoc().addLineClass(instruction.l - 1, 'wrap', 'running');
        } else {
          lastExecutedLine = null;
        }
        break;

      case 'msg':
        $timeout(
          function() { $window.alert(instruction.m) },
          500,
          false
        );
        stopTimer();
        break;

      case 'log':
        $scope.console.push(
          {
            type: instruction.t === 'e' ? 'stderr' : 'stdout',
            text: instruction.m + '\n'
          }
        );
        $timeout(
          function() {
            var console = document.getElementById("console");
            console.scrollTop = console.scrollHeight;
          },
          0,
          false
        );
        break;
    }
  }

  function establishWebsocketConnection() {
    var keepAliveTimeout;

    dataStream = $websocket(
      (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + location.pathname + '/simulation'
    );
    $log.debug(new Date + ": websocket connection opened");

    if (location.pathname.indexOf('random') > -1) {
      dataStream.onOpen(function keepAlive() {
        keepAliveTimeout = $timeout(
          function() {
            $log.debug(new Date() + ": sending keep alive");
            dataStream.send({});
            keepAlive();
          },
          30000, // 55 seconds is Heroku's timeout
          false
        );
      });
    }
    dataStream.onMessage(function(message) {
      $log.debug(message.data);
      processRobotInstruction(JSON.parse(message.data));
    });
    dataStream.onClose(function() {
      $log.warn(new Date + ": websocket connection closed");
      if (keepAliveTimeout) {
        $timeout.cancel(keepAliveTimeout);
      }
      dataStream = null;
    });
  }

  $scope.lang = $cookies.get('lang') || 'java';
  $scope.console = [];
  $scope.robot = {
    display: 'none'
  };
  $scope.finish = {
    display: 'none'
  };
  $scope.walls = [
  ];
  $scope.elapsedTimeMillis = startTimeMillis;

  $scope.codemirrorLoaded = function(editor) {
    editor.setOption('lineWrapping', true);
    editor.setOption('lineNumbers', true);
    editor.setOption('matchBrackets', true);
    editor.setOption('indentUnit', 4);
    editor.setOption('indentWithTabs', true);
    editor.setOption('mode', 'text/x-java');

    $scope.codemirrorEditor = editor;
    initializeSource();
  };

  $scope.runSimulation = function() {
    if (!dataStream) establishWebsocketConnection();
    dataStream.send({
      lang: $scope.lang,
      source: $scope.source
    });
    saveSessionState();
    stopTimer();
    startTimeMillis = new Date().getTime();
    timerPromise = $interval(
      function() {
        $scope.elapsedTimeMillis = new Date().getTime() - startTimeMillis;
      },
      100
    );
  };

  $scope.changeLanguage = function() {
    initializeSource();
  };

  $scope.clearConsole = function() {
    $scope.console = [];
  };

  $scope.resetCode = function() {
    if ($window.confirm('Resets the program to the original version. All your changes will be lost. Are you sure?')) {
      $cookies.remove('source');
      loadSourceFromTemplate();
    }
  };

  establishWebsocketConnection();
});
