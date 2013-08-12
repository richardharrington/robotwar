(ns robotwar.test-programs)

(def multi-use-program
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
        200 TO A ")

(def index-data-program
  ; to test the INDEX/DATA pair of registers
  " 300 TO A
    1 TO INDEX
    DATA TO B")

(def random-program
  ; to test the RANDOM register
  " 1000 TO RANDOM
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A
    RANDOM TO A ")

(def speedy-program
  " 70 TO SPEEDX
    140 TO SPEEDY ")
