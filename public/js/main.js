;(function() {

    var BUFFER_LENGTH = 500;
    var MAX_FAST_FORWARD = 40;
    var STARTING_FAST_FORWARD = 15;
    var FPS = 60;
    var ROBOT_COLORS = ["#6aea2a", "#380bfa", "#fa2d0b", "#0bfaf7", "#faf20b"];
 
    // TODO: This game info should probably come from the server
    // in a preliminary ajax call.
    var GAME_INFO = {
        robotRadius: 8,
        robotXMax: 256.0,
        robotYMax: 256.0,
        gameSecondsPerTick: 0.0333333333333333333333333
    }

    function Worlds(bufferLength, constructorCallback) {
        
        // This constructor function mostly mixes in the behavior
        // of Queue, but it adds functionality to Queue's
        // dequeue method, to fetch new items from the server
        // when the queue is running low.

        var queue = new Queue();
        var gameId;
        var isFetching = false;

        function fetch(callback) {
            $.getJSON('worlds/' + gameId + '/' + bufferLength, function(data) {
                queue.enqueueArray(data);
                if (callback) callback();
            });
        }
        function dropMulti(fastForward) {
            queue.dropMulti(fastForward);
            if (!isFetching && queue.getLength() < bufferLength) {
                isFetching = true;
                fetch(function() {
                    isFetching = false;
                });
            }
        }

        $.getJSON('init', function(data) {
            gameId = data['id'];
            fetch(constructorCallback);
        });

        return {
            dropMulti:     dropMulti,
            enqueueArray:  queue.enqueueArray.bind(queue),
            isEmpty:       queue.isEmpty.bind(queue),
            getLength:     queue.getLength.bind(queue),
            peek:          queue.peek.bind(queue)
        }
    }

    var Canvas = function(el) {
        var width = parseInt(el.width);
        var height = parseInt(el.height);
        var roomForRobots = GAME_INFO.robotRadius * 2;
        var arenaWidth =  GAME_INFO.robotXMax + roomForRobots;
        var arenaHeight = GAME_INFO.robotYMax + roomForRobots;
        var scaleFactorX = parseInt(width / arenaWidth);
        var scaleFactorY = parseInt(height / arenaHeight);
        var offsetX = function(x) {
            return scaleFactorX * (GAME_INFO.robotRadius + x)
        }
        var offsetY = function(y) {
            return scaleFactorY * (GAME_INFO.robotRadius + y)
        }
        // TODO: regularize this here and on the server so that
        // the arena is always square, and there's no ambiguity or question,
        // like why are we using scaleFactorX here and don't need
        // scaleFactorY?
        var robotDisplayRadius = GAME_INFO.robotRadius * scaleFactorX;

        var ctx = el.getContext('2d');

        var drawRobot = function(robot, idx) {
            ctx.fillStyle = ROBOT_COLORS[idx];
            ctx.beginPath();
            ctx.arc(
                offsetX(robot["pos-x"]), 
                offsetY(robot["pos-y"]), 
                robotDisplayRadius, 
                0, 
                Math.PI * 2,
                true);
            ctx.fill();
        }
        
        var draw = function(world) {
            ctx.clearRect(0, 0, width, height);
            world.robots.forEach(drawRobot);
        }

        return {
            draw: draw
        };
    }

    function loop(worlds, interval, callback) {
        (function continueLoop(tick) {
            if (worlds.isEmpty()) {
                return;
            }
            callback();
            var nextTick = tick + interval;
            setTimeout(function() {
                continueLoop(nextTick);
            }, nextTick - Date.now());
        })(Date.now());
    }

    var fastForward = STARTING_FAST_FORWARD;
    var tickDuration = parseInt (GAME_INFO.gameSecondsPerTick * 1000);
    var frameDuration = parseInt (1000 / FPS);

    var debugAnimationCounter = 0;
    var debugSimulationCounter = 0;
    var debugSecondsCounter = 0;
    var debugStartTime = Date.now();

    var canvas = new Canvas($('#canvas')[0]);
    var worlds = new Worlds(BUFFER_LENGTH, function() {
        
        // TODO: remove this tick loop entirely,
        // and just have the animation loop calculate which
        // simulation to pick each time.
        
        loop(worlds, tickDuration, function() {
            debugSimulationCounter++;
            worlds.dropMulti(fastForward);
        });
        loop(worlds, frameDuration, function() {
            debugAnimationCounter++;
            canvas.draw(worlds.peek());
        });
        loop(worlds, 1000, function() {
            debugSecondsCounter++;
            console.log(Math.floor((Date.now() - debugStartTime) / 1000) + 
                " " + debugAnimationCounter + 
                " " + debugSimulationCounter);
        });
    });

    // Keyboard event listeners for fast-forward control
    $('body').bind('keydown', function(event) {
        if (event.which === 37) {
            fastForward = Math.max(fastForward - 1, 1);
        }
        if (event.which === 39) {
            fastForward = Math.min(fastForward + 1, MAX_FAST_FORWARD);
        }
        console.log("fast forward: " + fastForward);
    });
})();
