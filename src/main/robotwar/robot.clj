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
  {:idx         idx
   :pos-x       (:pos-x attributes)
   :pos-y       (:pos-y attributes)
   :aim         (:aim attributes)
   :damage      (:damage attributes)
   :v-x         0.0
   :v-y         0.0
   :desired-v-x 0.0
   :desired-v-y 0.0
   :shot-timer  0.0
   :brain       (brain/init-brain src-code (register/init-registers idx))})

(defn update-robots
  "takes a world and a function, and returns a world
  with its robots updated by passing them through the function"
  [world f]
  (update-in world [:robots] f))

(defn update-robot
  "takes a robot index, a world, and a function, and returns a world
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

(defn collide-two-robots
  "takes a vector of robots, two robot-indexes (an acting robot
  and a target robot), and returns a vector of robots with those
  two altered if the actor has collided with the target.
  Right now they're just behaving like square billiard balls --
  all momentum from one is transferred to the other when they collide.
  To account for overshoot during the tick, the position of the actor
  is set to but up against the target.
  Does not currently calculate damage. when it does, it will
  need to only assign each robot half the damage, because the other
  half will be assigned when the other robot it ticks through its own turn."
  [robots actor-idx target-idx]
  (let [actor (get robots actor-idx)
        target (get robots target-idx)
        dist-x (- (:pos-x target) (:pos-x actor))
        dist-y (- (:pos-y target) (:pos-y actor))
        abs-dist-x (Math/abs dist-x)
        abs-dist-y (Math/abs dist-y)
        min-dist (* ROBOT-RADIUS 2)
        colliding (and (< abs-dist-x min-dist)
                       (< abs-dist-y min-dist)
                       (if (> abs-dist-x abs-dist-y) :x :y))]
    (if colliding
      (let [new-actor (case colliding
                        :x (assoc
                             actor 
                             :damage (dec (:damage actor))
                             :v-x (:v-x target)
                             :pos-x (- (:pos-x target) 
                                        (Math/copySign min-dist dist-x)))
                        :y (assoc 
                             actor 
                             :damage (dec (:damage actor))
                             :v-y (:v-y target)
                             :pos-y (- (:pos-y target) 
                                       (Math/copySign min-dist dist-y))))
            new-target (case colliding
                         :x (assoc 
                              target 
                              :damage (dec (:damage target))
                              :v-x (:v-x actor))
                         :y (assoc 
                              target 
                              :damage (dec (:damage target))
                              :v-y (:v-y actor)))]
        {colliding (assoc robots actor-idx new-actor, target-idx new-target)})
      {nil robots})))

(defn collide-all-robots
  "takes a vector of robots and an actor-idx,
  and returns a vector of robots with any collisions that have occurred
  (may be at most one x-collision and at most one y-collision)."
  ; TODO: this is remarkably inefficient, and checks the collisions 
  ; twice in a lot of cases. Sort this out when we sort out the whole :x and :y issue.
  [robots actor-idx]
  (let [target-idxs (filter #(not= actor-idx %) (range (count robots)))
        collided-robots-x (or (some (fn [target-idx]
                                      (:x (collide-two-robots 
                                            robots 
                                            actor-idx 
                                            target-idx)))
                                    target-idxs)
                              robots)
        collided-robots-y (or (some (fn [target-idx]
                                      (:y (collide-two-robots 
                                            collided-robots-x 
                                            actor-idx 
                                            target-idx)))
                                    target-idxs)
                              collided-robots-x)]
    collided-robots-y))

(defn tick-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add support for collision with walls first (right now it just 
  stops when it gets there, and doesn't get damaged or bounce), 
  then support for collision with other robots." 
  [{robot-idx :idx :as robot} world]
  (if false
  ; replace this real damage line when we 
  ; get robot death implemented correctly: (if (<= (:damage robot) 0)
    world
    (let [ticked-world             (brain/tick-brain 
                                     robot 
                                     world 
                                     register/read-register 
                                     register/write-register)
          shot-timer-updated-world (update-robot 
                                     robot-idx 
                                     ticked-world 
                                     update-shot-timer)
          moved-world              (update-robot
                                     robot-idx
                                     shot-timer-updated-world
                                     move-robot)
          collision-detected-world (update-robots 
                                     moved-world 
                                     #(collide-all-robots % robot-idx))]  
      collision-detected-world)))

