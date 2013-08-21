(ns robotwar.robot
  (:use [robotwar.constants])
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]
            [robotwar.phys :as phys]))

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

(defn tick-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world]
  (if (<= (:damage robot) 0)
    world
    (let [new-world (brain/tick-brain 
                      robot 
                      world 
                      register/read-register 
                      register/write-register)
          new-robot (get-in new-world [:robots robot-idx])
          {:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y shot-timer]} new-robot 
          max-accel-x (if (pos? desired-v-x) MAX-ACCEL (- MAX-ACCEL))
          max-accel-y (if (pos? desired-v-y) MAX-ACCEL (- MAX-ACCEL))
          {new-pos-x :d new-v-x :v} (phys/d-and-v-given-desired-v 
                                      pos-x 
                                      v-x 
                                      desired-v-x 
                                      max-accel-x 
                                      *GAME-SECONDS-PER-TICK*)
          {new-pos-y :d new-v-y :v} (phys/d-and-v-given-desired-v 
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

