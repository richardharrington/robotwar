;(function() {

    var BUFFER_LENGTH = 500;
    var MAX_FAST_FORWARD = 40;
    var STARTING_FAST_FORWARD = 15;
    var FPS = 60;
    var ROBOT_COLORS = ["#6aea2a", "#380bfa", "#fa2d0b", "#0bfaf7", "#faf20b"];
    var GUN_LENGTH = 14;
    var GUN_WIDTH = 3;
 
    // TODO: This game info should probably come from the server
    // in a preliminary ajax call.
    var GAME_INFO = {
        robotRadius: 8,
        robotXMax: 256.0,
        robotYMax: 256.0,
        gameSecondsPerTick: 0.0333333333333333333333333
    }

    var Geom = {
        degreesToRadians: function(angle) {
            return angle * Math.PI / 180;
        },

        polarToCartesian: function(angle, d) {
            return {
                x: d * Math.cos(angle),
                y: d * Math.sin(angle)
            }
        }
    }

    function Worlds(bufferLength, constructorCallback) {
        
        // The constructor first gets a game id from the server,
        // then runs the first fetch.

        var queue = new Queue();
        var gameId;
        var isFetching = false;
        var previousWorld = null;
        var currentWorld = null;

        function fetch(callback) {
            $.getJSON('worlds/' + gameId + '/' + bufferLength, function(data) {
                queue.enqueueArray(data);
                if (callback) callback();
            });
        }
        function advance(fastForward) {
            previousWorld = currentWorld;
            queue.dropMulti(fastForward);
            currentWorld = queue.peek();
            if (!isFetching && queue.getLength() < bufferLength) {
                isFetching = true;
                fetch(function() {
                    isFetching = false;
                });
            }
        }

        $.getJSON('init', function(data) {
            gameId = data['id'];
            fetch(function() {
                currentWorld = queue.peek();
                constructorCallback();
            });
        });

        return {
            advance:          advance,
            finished:         queue.isEmpty.bind(queue),
            getPreviousWorld: function() {return previousWorld;},
            getCurrentWorld:  function() {return currentWorld;}
        }
    }

    var Canvas = function(el) {
        var width = parseInt(el.width);
        var height = parseInt(el.height);
        var roomForRobots = GAME_INFO.robotRadius * 2;
        var arenaWidth =  GAME_INFO.robotXMax + roomForRobots;
        var arenaHeight = GAME_INFO.robotYMax + roomForRobots;
        var scaleFactorX = width / arenaWidth;
        var scaleFactorY = height / arenaHeight;
        var scaleX = function(x) {
            return Math.round(x * scaleFactorX);
        }
        var scaleY = function(y) {
            return Math.round(y * scaleFactorY);
        }
        var offsetX = function(x) {
            return scaleX(GAME_INFO.robotRadius + x);
        }
        var offsetY = function(y) {
            return scaleY(GAME_INFO.robotRadius + y);
        }
        
        // TODO: regularize this here and on the server so that
        // the arena is always square, and there's no ambiguity or question,
        // like why are we using scaleFactorX here and don't need
        // scaleFactorY?
       
        var robotDisplayRadius = scaleX(GAME_INFO.robotRadius);
        var gunDisplayLength = scaleX(GUN_LENGTH);
        var gunDisplayWidth = scaleY(GUN_WIDTH);
        
        var ctx = el.getContext('2d');
        ctx.lineWidth = gunDisplayWidth;
        ctx.lineCap = 'square';

        var drawCircle = function(x, y, r, color) {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(x, y, r, 0, Math.PI * 2, true);
            ctx.fill();
        }

        var drawLinePolar = function(x, y, angle, d, color) {
            var delta = Geom.polarToCartesian(angle, d);
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x + delta.x, y + delta.y);
            ctx.strokeStyle = color;
            ctx.stroke();
        }

        var drawRobot = function(robot, color) {
            var x = scaleX(robot['pos-x']);
            var y = scaleY(robot['pos-y']);
            drawCircle(x, y, robotDisplayRadius, color);
            drawLinePolar(x, y, Geom.degreesToRadians(robot['aim']), gunDisplayLength, color); 
        }
        
        var drawWorld = function(world) {
            ctx.clearRect(0, 0, width, height);
            world.robots.forEach(function(robot, idx) {
                drawRobot(robot, ROBOT_COLORS[idx]);
            });
        }

        return {
            drawWorld: drawWorld
        };
    }

    function loop(worlds, interval, callback) {
        (function continueLoop(tick) {
            if (worlds.finished()) {
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
            worlds.advance(fastForward);
        });
        loop(worlds, frameDuration, function() {
            debugAnimationCounter++;
            canvas.drawWorld(worlds.getPreviousWorld(), worlds.getCurrentWorld());
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
