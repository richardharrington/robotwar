;(function() {

    var debugAnimationCounter = 0;
    var debugSimulationCounter = 0;

    var ROBOT_COLORS = ["#6aea2a", "#380bfa", "#fa2d0b", "#0bfaf7", "#faf20b"];

    // TODO: This game info should probably come from the server
    // in a preliminary ajax call.
    var GAME_INFO = {
        robotRadius: 8,
        robotXMax: 256.0,
        robotYMax: 256.0,
        gameSecondsPerTick: 0.03
    }

    var canvas = (function(el) {
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
            console.log("ji");
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

        return {draw: draw};
    })($('#canvas')[0]);

    var tickQueue = new Queue();
    var fastForward = 5;

    // fastForward can't be more than 5 if we want tickDuration to be greater
    // than 6 milliseconds, which is close to the official 4-millisecond limit 
    // for setTimeout. TODO: set this as a limit in the user interface, 
    // and also look into how we can speed up by dropping ticks, while
    // still having things like having collisions happen when they're supposed
    // to happen. Perhaps if we make sure to animate collisions over 
    // several ticks, it will work.

    var tickDuration = parseInt (GAME_INFO.gameSecondsPerTick / fastForward * 1000);

    var fps = 60;
    var frameDuration = parseInt (1000 / fps);

    var animationLoopId;
    var debugTimeLoopInterval;

    var worlds = (function() {
        var isFetching = false;
        return {
            fetch: function() {
                if (isFetching) {
                    return;
                }
                isFetching = true;
                fetchWorlds(function() {
                    isFetching = false;
                });
            }
        };
    })();
    
    function simulationLoop(tick) {
        debugSimulationCounter++;
        if (tickQueue.isEmpty()) {
            clearTimeout(animationLoopId);
            clearInterval(debugTimeLoopInterval);
        }
        if (tickQueue.isEmpty()) {
            return;
        }
        if (tickQueue.getLength() < 500) {
            worlds.fetch();
        }
        tickQueue.dequeue();
        var nextTick = tick + tickDuration;
        setTimeout(function() {
            simulationLoop(nextTick);
        }, nextTick - Date.now()); 
    }

    function animationLoop(frame) {
        debugAnimationCounter++;
        if (tickQueue.isEmpty()) {
            return;
        }
        canvas.draw(tickQueue.peek());
        var nextFrame = frame + frameDuration;
        animationLoopId = setTimeout(function() {
            animationLoop(nextFrame);
        }, nextFrame - Date.now());
    }

    function debugTimeLoop() {
        var start = Date.now();
        debugTimeLoopInterval = setInterval(function() {
            console.log(Math.floor((Date.now() - start) / 1000) + " " + debugAnimationCounter + " " + debugSimulationCounter);
        }, 1000);
    }

    function fetchWorlds(callback) {
        $.getJSON('worlds/500', function(worlds) {
            tickQueue.enqueueArray(worlds);
            if (callback) callback();
        });
    }

    fetchWorlds(function() {
        var now = Date.now();
        simulationLoop(now);
        animationLoop(now);
        debugTimeLoop();
    });
})();
