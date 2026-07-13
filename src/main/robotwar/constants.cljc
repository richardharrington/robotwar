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
; Doubled from 15/25 on 2026-07-13: battles were dragging because
; ordinary bumps cost almost nothing under the quadratic falloff.
; If contact still feels free after playtesting, the agreed next step
; is switching the falloff from quadratic to linear (see
; resources/ai-specs/polishes-jul-12-spec.md), not more doubling.
(def MAX-WALL-DAMAGE 30.0)
(def MAX-COLLISION-DAMAGE 50.0)
(def V-MAX 25.5)
