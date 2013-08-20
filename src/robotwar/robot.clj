(ns robotwar.robot
  (:use [robotwar.constants])
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]))

; yay classical mechanics

(defn time-to-reach-desired-v
  [vi vf a]
  (let [v-diff (- vf vi)]
    (if (zero? v-diff)
      0.0
      (double (/ v-diff a)))))

(defn d-with-constant-a
  [d vi a t]
  (+ d (* vi t) (/ (* a (Math/pow t 2)) 2)))

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
      {:d (d-with-constant-a (d-with-constant-a d vi a t') vf 0.0 (- t t')) 
       :v vf})))

; TODO: deal with bumping into walls and other robots.

(defn init-robot
  "takes a robot-idx, a program, and a robot attribute map and returns a robot.
  The distance and distance/time units are all in decimeters and
  decimeters per second. Yes, you read that right. Don't ask. It fits
  best with the original specs of the game."
  [idx src-code attributes]
  (into attributes
        {:idx idx
         :v-x 0.0
         :v-y 0.0
         :desired-v-x 0.0
         :desired-v-y 0.0
         :shot-timer 0.0
         :brain (brain/init-brain src-code (register/init-registers idx))}))

(defn step-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world]
  (if (<= (:damage robot) 0)
    world
    (let [new-world (brain/step-brain 
                      robot 
                      world 
                      register/read-register 
                      register/write-register)
          new-robot (get-in new-world [:robots robot-idx])
          {:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y shot-timer]} new-robot 
          max-accel-x (if (pos? desired-v-x) MAX-ACCEL (- MAX-ACCEL))
          max-accel-y (if (pos? desired-v-y) MAX-ACCEL (- MAX-ACCEL))
          {new-pos-x :d new-v-x :v} (d-and-v-given-desired-v 
                                      pos-x 
                                      v-x 
                                      desired-v-x 
                                      max-accel-x 
                                      *GAME-SECONDS-PER-TICK*)
          {new-pos-y :d new-v-y :v} (d-and-v-given-desired-v 
                                      pos-y 
                                      v-y 
                                      desired-v-y 
                                      max-accel-y 
                                      *GAME-SECONDS-PER-TICK*)]
          (assoc-in 
            new-world 
            [:robots robot-idx] 
            (into new-robot {:pos-x new-pos-x
                             :pos-y new-pos-y
                             :v-x new-v-x
                             :v-y new-v-y
                             :shot-timer (max (- shot-timer *GAME-SECONDS-PER-TICK*)
                                              0.0)})))))

