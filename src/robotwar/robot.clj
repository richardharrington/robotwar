(ns robotwar.robot
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]))

; MAX_ACCEL is in decimeters per second per second. 
; TODO: should be passed in from some higher level module, or a config module.
(def MAX_ACCEL 40)

; yay classical mechanics

(defn time-to-reach-desired-v
  [vi vf a]
  (let [v-diff (- vf vi)]
    (if (zero? v-diff)
      0
      (double (/ v-diff a)))))

(defn d-with-constant-a
  [d vi a t]
  (double (+ d (* vi t) (/ (* a (Math/pow t 2)) 2))))

(defn v-with-constant-a
  [vi a t]
  (+ vi (* a t)))

(defn d-and-v-given-desired-v
  "returns a map of distance and velocity at final position.
  the function deals with either of two cases:
  1) when the desired velocity is not reached during the 
     given time interval, in which case it's just 
     distance-with-constant-acceleration 
  2) when we reach the desired velocity (or are already there)
     and then cruise the rest of the way" 
  [d vi vf a t]
  (let [t' (time-to-reach-desired-v vi vf a)]
    (if (> t' t)
      {:d (d-with-constant-a d vi a t) 
       :v (v-with-constant-a vi a t)}
      {:d (d-with-constant-a (d-with-constant-a d vi a t') vf 0 (- t t')) 
       :v vf})))

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
   :desired-v-x 0
   :desired-v-y 0
   :aim (:aim attributes)
   :damage (:damage attributes)
   :brain (brain/init-brain src-code (register/init-registers idx))})

(defn step-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world tick-duration]
  (if (>= (:damage robot) 100)
    world
    (let [new-world (brain/step-brain 
                      robot 
                      world 
                      register/read-register 
                      register/write-register)
          new-robot (get-in new-world [:robots robot-idx])
          {:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y]} new-robot 
          max-accel-x (if (pos? desired-v-x) MAX_ACCEL (- MAX_ACCEL))
          max-accel-y (if (pos? desired-v-y) MAX_ACCEL (- MAX_ACCEL))
          {new-pos-x :d new-v-x :v} (d-and-v-given-desired-v 
                                      pos-x v-x desired-v-x max-accel-x tick-duration)
          {new-pos-y :d new-v-y :v} (d-and-v-given-desired-v 
                                      pos-y v-y desired-v-y max-accel-y tick-duration)]
          (assoc-in 
            new-world 
            [:robots robot-idx] 
            (into new-robot {:pos-x pos-x
                             :pos-y pos-y
                             :v-x v-x
                             :v-y v-y})))))

