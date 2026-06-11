(ns robotwar.constants)

; MAX_ACCEL is in decimeters per second per second. 
(def MAX-ACCEL 4.0)
(def ^:dynamic *GAME-SECONDS-PER-TICK* 0.033)

; ROBOT-RANGE-X and -Y are in meters
(def ROBOT-RANGE-X 256.0)
(def ROBOT-RANGE-Y 256.0)

(def GAME-SECONDS-PER-SHOT 20.0)

; SHELL-SPEED is in meters per second
(def SHELL-SPEED 25.0)

; Robot-radius is in meters.
(def ROBOT-RADIUS 7.0)

; Damage and collision constants (absolute meters, not dependent on ROBOT-RADIUS)
(def MAX-BLAST-DAMAGE 30.0)
(def BLAST-RADIUS 21.0)
(def MAX-WALL-DAMAGE 15.0)
(def MAX-COLLISION-DAMAGE 25.0)
(def V-MAX 25.5)
