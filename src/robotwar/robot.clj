(ns robotwar.robot
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]))

; TICK_DURATION is in seconds. MAX_ACCEL is in decimeters per second per second. 
; TODO: should be passed in from some higher level module, or a config module.
(def TICK_DURATION 1)
(def MAX_ACCEL 40)

; yay classical mechanics

(defn time-to-reach-desired-v
  [vi vf a]
  (let [v-diff (- vf vi)]
    (if (zero? v-diff)
      0
      (double (/ v-diff a)))))

(defn d-with-constant-a
  [vi a t]
  (double (+ (* vi t) (/ (* a (Math/pow t 2)) 2))))

(defn v-with-constant-a
  [vi a t]
  (+ vi (* a t)))

(defn d-and-v-given-desired-v
  "returns a vector of distance and velocity at final position.
  the function deals with either of two cases:
  1) when the desired velocity is not reached during the 
  given time interval, in which case it's just 
  distance-from-constant-acceleration, and 
  2) when we reach the desired velocity (or are already there)
  and then cruise the rest of the way." 
  [vi vf a t]
  (let [t' (time-to-reach-desired-v vi vf a)]
    (if (<= t t')
      [(d-with-constant-a vi a t) 
       (v-with-constant-a vi a t)]
      [(+ (d-with-constant-a vi a t') (d-with-constant-a vf 0 (- t t'))) 
       vf])))

; TODO: velocity-given-desired-velocity-and-distance or something 
; like that, to figure out the velocity at the point when
; we collide with the wall or another robot, so we can know the kinetic
; energy and thus the damage.
;
; TODO: fix any bugs, then account for initial distance in these calculations.
; I think it would go into the basic physics calculations; why not? 
; ALSO: deal with acceleration being negative, when it should be.
; And deal with bumping into walls.

(defn init-robot
  "takes a robot-idx, a program, and a robot attribute map and returns a robot.
  The distance and distance/time units are all in decimeters and
  decimeters per second. Yes, you read that right. Don't ask. It fits
  best with the original specs of the game."
  [idx src-code attributes]
  {:idx idx
   :pos-x (:pos-x attributes)
   :pos-y (:pos-y attributes)
   :v-x 0
   :v-y 0
   :aim (:aim attributes)
   :damage (:damage attributes)
   :registers (register/init-registers idx)
   :brain (brain/init-brain src-code register/reg-names)})

(defn step-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world]
  (if (>= (:damage robot) 100)
    world
    (let [new-world (brain/step-brain robot world)
          new-robot (get-in new-world [:robots robot-idx])
          desired-v-x (register/read-register 
                        (get-in new-robot [:registers "SPEEDX"]) 
                        new-world)
          desired-v-y (register/read-register 
                        (get-in new-robot [:registers "SPEEDY"]) 
                        new-world)
          [pos-x v-x] (d-and-v-given-desired-v (:v-x robot) desired-v-x 
                                               MAX_ACCEL TICK_DURATION)
          [pos-y v-y] (d-and-v-given-desired-v (:v-y robot) desired-v-y 
                                               MAX_ACCEL TICK_DURATION)]
      (assoc-in 
        new-world 
        [:robots robot-idx] 
        (into new-robot {:pos-x pos-x
                         :pos-y pos-y
                         :v-x v-x
                         :v-y v-y})))))

