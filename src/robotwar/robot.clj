(ns robotwar.robot
  (:use [robotwar.constants])
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]
            [robotwar.physics :as physics]))

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

(defn update-robot
  "takes a robot, a world, and a function, and returns a world
  with the robot updated by passing it through the function"
  [robot world f]
  (update-in world [:robots (:idx robot)] f))

(defn update-shot-timer
  "takes a robot and returns one with the :shot-timer updated"
  [{shot-timer :shot-timer :as robot}]
  (merge robot {:shot-timer 
                (max (- shot-timer *GAME-SECONDS-PER-TICK*) 0.0)}))

(defn move-robot
  "takes a robot and returns it, moved through space.
  helper function for tick-robot."
  [{:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y] :as robot}]
  (let [max-accel-x (Math/copySign MAX-ACCEL desired-v-x)
        max-accel-y (Math/copySign MAX-ACCEL desired-v-y)
        {new-pos-x :d, new-v-x :v} (physics/d-and-v-given-desired-v 
                                     pos-x 
                                     v-x 
                                     desired-v-x 
                                     max-accel-x 
                                     *GAME-SECONDS-PER-TICK*)
        {new-pos-y :d, new-v-y :v} (physics/d-and-v-given-desired-v 
                                     pos-y 
                                     v-y 
                                     desired-v-y 
                                     max-accel-y 
                                     *GAME-SECONDS-PER-TICK*)]
    (merge robot {:pos-x new-pos-x
                  :pos-y new-pos-y
                  :v-x new-v-x
                  :v-y new-v-y})))

(defn collide-or-not
 "takes a robot and a world and returns the world, with the
 velocities of robots altered if they have collided with
 each other. Does not currently calculate damage to robots."
 [{robot-idx :idx :as robot} {robots :robots :as world}]
 (let [other-robots (concat (take robot-idx robots)
                            (drop (inc robot-idx robots)))
       colliding-x ]





(defn tick-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world]
  (if (<= (:damage robot) 0)
    world
    (let [ticked-world (brain/tick-brain 
                         robot 
                         world 
                         register/read-register 
                         register/write-register)
          shot-timer-updated-world (update-robot robot ticked-world update-shot-timer)
          moved-robot-world (update-robot robot shot-timer-updated move-robot)]





      (update-in ticked-world [:robots robot-idx] (comp update-shot-timer move-robot)))))
