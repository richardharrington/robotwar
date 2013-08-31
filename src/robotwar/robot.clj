(ns robotwar.robot
  (:use [robotwar.constants]
        [clojure.pprint :only [pprint]])
  (:require [robotwar.brain :as brain]
            [robotwar.register :as register]
            [robotwar.physics :as physics]))

; TODO: deal with bumping into walls.

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
  [robot-idx world f]
  (update-in world [:robots robot-idx] f))

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
  each other. Does not currently calculate damage to robots.
  TODO: there's got to be a better way to write this. The whole last
  two-thirds consists of code that would be a lot shorter even in JavaScript,
  for Christ's sake."
  [robot-idx {robots :robots :as world}]
  (let [robot (get-in world [:robots robot-idx])
        other-robot-idxs (filter #(not= robot-idx %) (range (count robots)))
        enemy-colliding-x? (fn [other-robot-idx]
                             (let [other-robot (get-in world [:robots other-robot-idx])]
                               (< (Math/abs (- (:pos-x robot) (:pos-x other-robot))) 
                                  (* ROBOT-RADIUS 2))))
        enemy-colliding-y? (fn [other-robot-idx]
                             (let [other-robot (get-in world [:robots other-robot-idx])]
                               (< (Math/abs (- (:pos-y robot) (:pos-y other-robot))) 
                                  (* ROBOT-RADIUS 2))))
        colliding-enemy-idxs-x (set (filter enemy-colliding-x? other-robot-idxs))
        colliding-enemy-idxs-y (set (filter enemy-colliding-y? other-robot-idxs))
        total-colliding-idxs-x (if (not-empty colliding-enemy-idxs-x)
                                 (conj colliding-enemy-idxs-x robot-idx)
                                 #{})
        total-colliding-idxs-y (if (not-empty colliding-enemy-idxs-y)
                                 (conj colliding-enemy-idxs-y robot-idx)
                                 #{})
        new-robots-v-x (mapv (fn [{rob-idx :idx :as rob}]
                               (if (get total-colliding-idxs-x rob-idx)
                                 (assoc rob :v-x 0.0)
                                 rob))
                             robots)
        new-robots-v-y (mapv (fn [{rob-idx :idx :as rob}]
                               (if (get total-colliding-idxs-y rob-idx)
                                 (assoc rob :v-y 0.0)
                                 rob))
                             new-robots-v-x)]
    (assoc world :robots new-robots-v-y)))

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
          shot-timer-updated-world (update-robot robot-idx ticked-world update-shot-timer)
          collision-detected-world (collide-or-not robot-idx shot-timer-updated-world)]  
      (update-robot robot-idx collision-detected-world move-robot))))

