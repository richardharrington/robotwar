(ns robotwar.source-programs)

(def programs
  {:multi-use
  " START 
        0 TO A
    TEST 
        IF A > 2 GOTO START 
        GOSUB INCREMENT
        GOTO TEST 
        100 TO A 
    INCREMENT 
        A + 1 TO A 
        ENDSUB 
        200 TO A "
  
  :index-data
  ; to test the INDEX/DATA pair of registers
  " 300 TO A
    1 TO INDEX
    DATA TO B"
  
  :random
  ; to test the RANDOM register
  " 1000 TO RANDOM
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A "

 :speedy
 " 140 TO SPEEDX
   250 TO SPEEDY "

 :moving-to-spot
 "
                              ; Note: # means !=

   360 TO RANDOM              
   RANDOM TO AIM              ; Set a random direction to aim the gun
 
   256 TO RANDOM              ; All random numbers will now have as their maximum
                              ; the width and height of the arena (in meters).
  
   LOOP
       0 TO SPEEDX            ; Stop the robot (X). 
       0 TO SPEEDY            ; Stop the robot (Y).
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
       ENDSUB "
  
 :shooter
 "
       90 TO AIM
       Y TO RANDOM
       RANDOM TO S
   SHOOT
       ; S TO SHOT
       GOTO SHOOT "
   }) 
