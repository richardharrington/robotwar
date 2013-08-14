(ns robotwar.test-programs)

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
   256 TO RANDOM              ; the width and height of the arena
  
   LOOP
       0 TO SPEEDX
       0 TO SPEEDY
       RANDOM TO A
       RANDOM TO B
   
   MOVE
       IF A # X GOSUB MOVEX
       TO N                   ; N means no-op
       IF B # Y GOSUB MOVEY
       IF A = X GOTO LOOP 
       GOTO MOVE
        
   MOVEX 
       A - X TO SPEEDX
       ENDSUB
   
   MOVEY
       B - Y TO SPEEDY
       ENDSUB "}) 
