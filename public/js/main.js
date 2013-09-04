;(function() {

    var BUFFER_LENGTH = 1000;
    var MAX_FAST_FORWARD = 40;
    var STARTING_FAST_FORWARD = 15;
    var FPS = 60;
    var ROBOT_COLORS = ["#fa2d0b", "#0bfaf7", "#faf20b", "#e312f0", "#4567fb"];
    var SHELL_RADIUS = 2;
    var SHELL_COLOR = "#ffffff";

    var Geom = (function() {
        var degreesToRadians = function(angle) {
            return angle * Math.PI / 180;
        }
        var RWDegreesToJSDegrees = function(angle) {
            return angle - 90;
        }
        var polarToCartesian = function(angleInDegrees, d) {
            var angle = degreesToRadians(RWDegreesToJSDegrees(angleInDegrees));
            return {
                x: d * Math.cos(angle),
                y: d * Math.sin(angle)
            }
        }
        return {
            polarToCartesian: polarToCartesian
        }
    })();

    function Worlds(programs, bufferLength) {
        
        // The constructor first gets a game id from the server,
        // then runs the first fetch.

        var queue = new Queue();
        var gameId;
        var gameInfo;
        var onLoaded = $.Deferred();
        var isFetching = false;
        var areWeFinished = false;
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

            // if the queue has run out, just stall with the 
            // existing currentWorld until we receive a new one.

            currentWorld = queue.peek() || currentWorld;
            if (!isFetching && queue.getLength() < bufferLength) {
                isFetching = true;
                fetch(function() {
                    isFetching = false;
                });
            }
        }
        function finish() {
            areWeFinished = true;
        }
        function isFinished() {
            return areWeFinished;
        }
        function getGameInfo() {
            return gameInfo;
        }
        $.getJSON('init?programs=' + encodeURIComponent(programs))
        .done(function(data) {
            gameId = data['id'];
            var gameInfoFromServer = data['game-info'];
            gameInfo = {
                robotRadius: gameInfoFromServer["ROBOT-RADIUS"],
                robotRangeX: gameInfoFromServer["ROBOT-RANGE-X"],
                robotRangeY: gameInfoFromServer["ROBOT-RANGE-Y"],
                gameSecondsPerTick: gameInfoFromServer["*GAME-SECONDS-PER-TICK*"]
            };
            console.log(gameInfo);
            fetch(function() {
                currentWorld = queue.peek();
                onLoaded.resolve();
            });
        });

        return {
            advance:          advance,
            finish:           finish,
            isFinished:       isFinished,
            getGameInfo:      getGameInfo,
            onLoaded:         onLoaded,
            getPreviousWorld: function() {return previousWorld;},
            getCurrentWorld:  function() {return currentWorld;}
        }
    }

    var Animation = function(el, sounds, gameInfo) {
        var width = parseInt(el.width);
        var height = parseInt(el.height);
        var roomForRobots = gameInfo.robotRadius * 2;
        var arenaWidth =  gameInfo.robotRangeX + roomForRobots;
        var arenaHeight = gameInfo.robotRangeY + roomForRobots;
        var scaleFactorX = width / arenaWidth;
        var scaleFactorY = height / arenaHeight;
        var scaleX = function(x) {
            return Math.round(x * scaleFactorX);
        }
        var scaleY = function(y) {
            return Math.round(y * scaleFactorY);
        }
        var offsetX = function(x) {
            return scaleX(gameInfo.robotRadius + x);
        }
        var offsetY = function(y) {
            return scaleY(gameInfo.robotRadius + y);
        }
        
        // TODO: regularize this here and on the server so that
        // the arena is always square, and there's no ambiguity or question,
        // like why are we using scaleFactorX here and don't need
        // scaleFactorY?
       
        var robotDisplayRadius = scaleX(gameInfo.robotRadius);
        var shellDisplayRadius = scaleX(gameInfo.robotRadius * 0.3);
        var gunDisplayLength = scaleX(gameInfo.robotRadius * 1.4);
        var gunDisplayWidth = scaleX(gameInfo.robotRadius * 0.5);
        
        var ctx = el.getContext('2d');
        ctx.lineCap = 'square';

        var nextSoundEl = (function() {
            var i = 0;
            return {
                get: function() {
                    var el = sounds[i];
                    i = (i + 1) % 5;
                    return el;
                }
            }
        })();

        // TODO: this whole drawing section is kind of hacky. reorganize
        // the behaviors of these functions to be less redundant,
        // and more abstracted.
        
        var fillCircle = function(x, y, r, color) {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(x, y, r, 0, Math.PI * 2, true);
            ctx.fill();
        }

        var strokeCircle = function(x, y, r, lineWidth) {
            ctx.lineWidth = lineWidth;
            ctx.strokeStyle = "#000";
            ctx.beginPath();
            ctx.arc(x, y, r, 0, Math.PI * 2, true);
            ctx.stroke();
        }

        var fillSquare = function(x, y, size, color) {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.rect(x - size / 2, y - size / 2, size, size);
            ctx.fill();
        }

        var drawLinePolar = function(x, y, angle, d, lineWidth, color) {
            var delta = Geom.polarToCartesian(angle, d);
            ctx.lineWidth = lineWidth;
            ctx.strokeStyle = color;
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x + delta.x, y + delta.y);
            ctx.strokeStyle = color;
            ctx.stroke();
        }

        var drawRobot = function(robot, color) {
            var x = offsetX(robot['pos-x']);
            var y = offsetY(robot['pos-y']);
            fillSquare(x, y, robotDisplayRadius * 2, color);
            strokeCircle(x, y, robotDisplayRadius * 0.6, gunDisplayWidth * 0.3);
            drawLinePolar(x, y, robot['aim'], gunDisplayLength, gunDisplayWidth, color); 
        }

        var drawShell = function(shell) {
            var x = offsetX(shell['pos-x']);
            var y = offsetY(shell['pos-y']);
            fillCircle(x, y, shellDisplayRadius, SHELL_COLOR);
        }
        
        var explodeShell = function(shell) {
            var x = offsetX(shell['pos-x']);
            var y = offsetY(shell['pos-y']);
            fillCircle(x, y, shellDisplayRadius * 10, SHELL_COLOR);
        }
        
        var animateWorld = function(previousWorld, currentWorld) {
            ctx.clearRect(0, 0, width, height);
            var currentShells = currentWorld["shells"];
            var previousShells = previousWorld["shells"];
            for (key in previousShells) {
                if (previousShells.hasOwnProperty(key)) {
                    if (currentShells.hasOwnProperty(key)) {
                        drawShell(previousShells[key]);
                    }
                    else {
                        explodeShell(previousShells[key]);
                    }
                }
            }
            currentWorld.robots.forEach(function(robot, idx) {
                if (previousWorld.robots[idx]["damage"] !== robot["damage"]) {
                    drawRobot(robot, "#fff");
                }
                else {
                    drawRobot(robot, ROBOT_COLORS[idx]);
                }
            });

            if (currentWorld["next-shell-id"] !== previousWorld["next-shell-id"]) {
                nextSoundEl.get().play();
            }
        }

        return {
            animateWorld: animateWorld
        };
    }

    function loop(worlds, interval, callback) {
        (function continueLoop(tick) {
            if (worlds.isFinished()) {
                return;
            }
            callback();

            var nextTick = tick + interval;
            setTimeout(function() {
                continueLoop(nextTick);
            }, nextTick - Date.now());
        })(Date.now());
    }

    function startGame(worlds) {
        var gameInfo = worlds.getGameInfo();
        var debugAnimationCounter = 0;
        var debugSimulationCounter = 0;
        var debugSecondsCounter = 0;
        var debugStartTime = Date.now();
        
        var fastForward = STARTING_FAST_FORWARD;
        var tickDuration = parseInt (gameInfo.gameSecondsPerTick * 1000);
        var frameDuration = parseInt (1000 / FPS);

        var canvasEl = $('#canvas')[0];
        var sounds = $('audio');

        var animation = new Animation(canvasEl, sounds, gameInfo);

        // TODO: remove this tick loop entirely,
        // and just have the animation loop calculate which
        // simulation to pick each time.
         
        loop(worlds, tickDuration, function() {
            debugSimulationCounter++;
            worlds.advance(fastForward);
        });
        loop(worlds, frameDuration, function() {
            debugAnimationCounter++;
            animation.animateWorld(worlds.getPreviousWorld(), worlds.getCurrentWorld());
        });
        loop(worlds, 1000, function() {
            debugSecondsCounter++;
            console.log(Math.floor((Date.now() - debugStartTime) / 1000) + 
                " " + debugAnimationCounter + 
                " " + debugSimulationCounter);
        });
         
        // Keyboard event listener for fast-forward control
        // TODO: dispose of this if we start a new worlds object.
        // Or just have it not mutate fastForward.
        $('body').bind('keydown', function(event) {
            if (event.which === 37) {
                fastForward = Math.max(fastForward - 1, 1);
            }
            if (event.which === 39) {
                fastForward = Math.min(fastForward + 1, MAX_FAST_FORWARD);
            }
            console.log("fast forward: " + fastForward);
        });
    }

    function init() {
        var worlds;

        // Text and keyboard event listeners for sending program names to server

        $('#programsInput').bind('keydown', function(event) {
            if (event.which === 13) {
                event.stopPropagation();
                event.preventDefault();
                if (worlds) {
                    worlds.finish();
                }

                // css animation and blurring of input box
                // (takes 1000 milliseconds total)

                $('.instruction-box').css({ height: 0 });
                setTimeout(function() {
                    $('#canvas').css({ opacity: 1 });
                    console.log("finished hiding instruction box");
                    console.log(Date.now());
                }, 500);
                $(this).blur();

                var programs = this.value;
                worlds = new Worlds(programs, BUFFER_LENGTH);
                
                // wait till at least the animation is done (1000 milliseconds) 
                // before we start the battle

                setTimeout(function() {
                    worlds.onLoaded.done(function() {
                        startGame(worlds);
                        console.log("finished loading first worlds");
                        console.log(Date.now());
                    });
                    console.log("finished showing arena");
                    console.log(Date.now());
                }, 1000);
            }
        });
        
        // Fetch list of robots for user 
        $.getJSON('program-names', function(data) {
            $('#programNames').text(data.names.join(", "));
            $('body').css({display: 'block'});
        });
    }

    // Fire it up.
    $(init);

})();
