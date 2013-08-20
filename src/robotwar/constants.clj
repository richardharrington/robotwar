(ns robotwar.constants)

; MAX_ACCEL is in decimeters per second per second. 
(def MAX-ACCEL 4.0)
(def ^:dynamic *GAME-SECONDS-PER-TICK* 0.03)

; ROBOT-RANGE-X and -Y are in meters
(def ROBOT-RANGE-X 256.0)
(def ROBOT-RANGE-Y 256.0)

(def GAME-SECONDS-PER-SHOT 1.0)
