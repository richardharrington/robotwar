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

 :mover
 "
                              ; Note: # means !=

   360 TO RANDOM              
   RANDOM TO AIM              ; Set a random direction to aim the gun
 
   256 TO RANDOM              ; All random numbers will now have as their maximum
                              ; the width and height of the arena (in meters).
  
   LOOP
       0 TO SPEEDX            ; STOP THE ROBOT!
       0 TO SPEEDY
       RANDOM TO A            ; Store a random X-coordinate in the arena.
       RANDOM TO B            ; Store a random Y-coordinate in the arena.
   
   MOVE
       AIM + 5 TO AIM
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

 :left-shooter
 "
                              ; Note: # means !=

       90 TO AIM              ; Set the gun to shoot right 
 
       15 TO A                ; Set the coordinates for the left side.
       128 TO B
   
   MOVE
       IF A # X GOSUB MOVEX   ; If we're moving in the X direction, recalibrate SPEEDX.
       TO N                   ; N is for no-op. (needed because there's no ELSE command).
       IF B # Y GOSUB MOVEY   ; If we're moving in the Y direction, recalibrate SPEEDY. 
       IF A = X GOTO SHOOT    ; A = X and B = Y, so we've stopped moving, so shoot.
       GOTO MOVE              ; Continue to move.
        
   MOVEX 
       A - X TO SPEEDX        ; Take distance from destination in meters and use
                              ; it to set SPEEDX, which is measured in decimeters/second.
       ENDSUB
   
   MOVEY
       B - Y TO SPEEDY        ; Take distance from destination in meters and use
                              ; it to set SPEEDY, which is measured in decimeters/second.
       ENDSUB 

   SHOOT
       200 TO SHOT
       GOTO MOVE "
 
 :top-shooter
 "
                              ; Note: # means !=

       180 TO AIM             ; Set the gun to shoot down 
 
       128 TO A                ; Set the coordinates for the top.
       15 TO B
   
   MOVE
       IF A # X GOSUB MOVEX   ; If we're moving in the X direction, recalibrate SPEEDX.
       TO N                   ; N is for no-op. (needed because there's no ELSE command).
       IF B # Y GOSUB MOVEY   ; If we're moving in the Y direction, recalibrate SPEEDY. 
       IF A = X GOTO SHOOT    ; A = X and B = Y, so we've stopped moving, so shoot.
       GOTO MOVE              ; Continue to move.
        
   MOVEX 
       A - X TO SPEEDX        ; Take distance from destination in meters and use
                              ; it to set SPEEDX, which is measured in decimeters/second.
       ENDSUB
   
   MOVEY
       B - Y TO SPEEDY        ; Take distance from destination in meters and use
                              ; it to set SPEEDY, which is measured in decimeters/second.
       ENDSUB 

   SHOOT
       200 TO SHOT
       GOTO MOVE "
 
 :shooter
 "
       90 TO AIM
       200 - Y TO S
   SHOOT
       S TO SHOT
       GOTO SHOOT "
   }) 
