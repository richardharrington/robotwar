RobotWar
--------

A reverse-engineered version (in Clojure) of Silas Warner's 1981 Apple II game, RobotWar.

In RobotWar, players write programs in a Forth-like language created specifically for the game, which is compiled down to a virtual machine code and then used as an AI for a (virtual) robot in battles against other players' robots. 

Here are some resources that describe the game pretty well:

* The original manual: ftp://ftp.apple.asimov.net/pub/apple_II/documentation/games/misc/Robotwar.pdf (markdown won't render this ftp site as a link, but if you copy and paste it into Chrome you should get the pdf you're looking for)
* [A blog post](http://www.filfre.net/2012/01/robot-war/) explaining the game in some detail

### To see it in action:

Install [leiningen](http://leiningen.org), then:

    git clone git@github.com:richardharrington/robotwar.git
    cd robotwar
    lein ring server
    
Then type in the names of the robots displayed in the browser (up to 5), and watch them move around the arena.

My implementation of it is currently a work in progress.  As you will note, the robots aren't actually damaging each other yet, but it has sound effects (the blaster effect from Star Wars), which as we know is 90% of the battle.

### Architecture

A brief description of the code, divided by namespace:

#### Assembler

This is where the source code is lexed, parsed, and assembled down to its object code, which consists mostly of command-argument pairs and which is implement as follows (the comma command in the object code stands for 'push to accumulator'):

    X - 17 TO AIM

becomes

    [{:val ",", :type :command} 
     {:val "X", :type :register}] 
    [{:val "-", :type :command} 
     {:val 17, :type :number}] 
    [{:val "TO", :type :command} 
     {:val "AIM", :type :register}]]

#### Brain

This contains the interpreter that executes each robot program's object code during a battle.

#### Register

The robot's brain conducts all of its operations on one accumulator (like a data stack, but only holds one item) and a series of pre-defined 'registers', which are in some cases used for simple storage but in other cases are overloaded I/O functions. For instance, when a number is written to the RADAR register, it is interpreted in degrees as an angle to pulse the radar, while reading from the RADAR register may yield a distance from the enemy, in meters. So the register namespace implements all this disparate behavior and hides it from the brain.

#### Robot

This is where the actual state of the robot is stored, within the context of the arena. This namespace functions as an interface between the brain and the wider world of the game, including the other robots. It contains a tick function which in turn ticks the brain, then executes other code to determine the fate of the robot in the world. Note: for simplicity's sake at the current time, each robot takes turns and alters the world within its own tick; it would be better to tick each robot's brain, see what they wanted to do, and then reconcile their actions, but that is not implemented yet.

#### World

Ticks all the robots, then ticks the shells that are travelling through the air at any given time (the shells are not implemented yet).

#### Animate

Has two parts: one for basic animation in the terminal, the other to prepare a simplified version of a world-state to send to the browser for display, as JSON.

#### Handler

An http request handler so that each world state can be sent to a browser for animation using Canvas.

#### Example:

A full description of the RobotWar language can be found on [this page](http://corewar.co.uk/robotwar/robotwar.txt), but here is a short example program for a robot that loops through picking a random spot on the screen, accelerating there and slowing down as it arrives, then repeating the process (it doesn't actually shoot at anything, so it wouldn't do well in a real battle):

                               ; Note: # means !=
    
    256 TO RANDOM              ; All random numbers will now have as their maximum
                               ; the width and height of the arena (in meters).
    
    LOOP
        0 TO SPEEDX TO SPEEDY  ; Stop the robot (X and Y). 
        RANDOM TO A            ; Store a random X-coordinate in the arena.
        RANDOM TO B            ; Store a random Y-coordinate in the arena.
    
    MOVE
        IF A # X GOSUB MOVEX   ; If we're moving in the X direction, recalibrate SPEEDX.
        TO N                   ; N is for no-op. (needed because there's no ELSE command).
        IF B # Y GOSUB MOVEY   ; If we're moving in the Y direction, recalibrate SPEEDY. 
        IF A = X GOTO LOOP     ; A = X and B = Y, so we've stopped moving, so start over. 
        GOTO MOVE              ; Continue to move.
         
    MOVEX 
        A - X TO SPEEDX        ; Take distance from destination in meters and use
                               ; it to set SPEEDX, which is measured in decimeters/second.
        ENDSUB
    
    MOVEY
        B - Y TO SPEEDY        ; Take distance from destination in meters and use
                               ; it to set SPEEDY, which is measured in decimeters/second.
        ENDSUB  
    
And here is what it compiles to in the virtual machine code (the comma command means push to the accumulator):

        ,        256
        TO       RANDOM
    LOOP
        ,        0
        TO       SPEEDX
        TO       SPEEDY
        ,        RANDOM
        TO       A
        ,        RANDOM
        TO       B
    MOVE
        IF       A
        #        X
        GOSUB    MOVEX
        TO       N
        IF       B
        #        Y
        GOSUB    MOVEY
        IF       A
        =        X
        GOTO     LOOP
        GOTO     MOVE
    MOVEX
        ,        A
        -        X
        TO       SPEEDX
        ENDSUB
    MOVEY
        ,        B
        -        Y
        TO       SPEEDY
        ENDSUB
        
        
            
