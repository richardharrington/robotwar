(ns robotwar.robot
  (:require [robotwar.constants :refer [*GAME-SECONDS-PER-TICK* MAX-ACCEL ROBOT-RADIUS
                                        ROBOT-RANGE-X ROBOT-RANGE-Y
                                        MAX-WALL-DAMAGE V-MAX]]
            [robotwar.brain :as brain]
            [robotwar.register :as register]
            [robotwar.physics :as physics]))

(defn rw-abs [x]
  #?(:clj (Math/abs x)
     :cljs (js/Math.abs x)))

(defn rw-copy-sign [magnitude sign]
  #?(:clj (Math/copySign magnitude sign)
     :cljs (* (rw-abs magnitude) (if (neg? sign) -1 1))))

(defn init-robot
  "takes a robot-idx, a program, and a robot attribute map and returns a robot.
  The distance and distance/time units are all in decimeters and
  decimeters per second. Yes, you read that right. Don't ask. It fits
  best with the original specs of the game."
  [idx src-code attributes]
  {:idx            idx
   :pos-x          (:pos-x attributes)
   :pos-y          (:pos-y attributes)
   :aim            (:aim attributes)
   :damage         (:damage attributes)
   :alive?         true
   :v-x            0.0
   :v-y            0.0
   :desired-v-x    0.0
   :desired-v-y    0.0
   :shot-timer     0.0
   :touching-walls #{}
   :brain          (brain/init-brain src-code (register/init-registers idx))})

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

(defn- wall-damage
  "quadratic falloff in the perpendicular velocity component"
  [v-perp]
  (let [factor (/ (rw-abs v-perp) V-MAX)]
    (* MAX-WALL-DAMAGE factor factor)))

(defn move-robot
  "takes a robot and returns it, moved through space (with wall clamping,
  slide behavior, and first-contact wall damage applied).
  helper function for tick-robot."
  [{:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y damage touching-walls] :as robot}]
  (let [max-accel-x (rw-copy-sign MAX-ACCEL desired-v-x)
        max-accel-y (rw-copy-sign MAX-ACCEL desired-v-y)
        {raw-pos-x :d, raw-v-x :v} (physics/d-and-v-given-desired-v
                                     pos-x
                                     v-x
                                     desired-v-x
                                     max-accel-x
                                     *GAME-SECONDS-PER-TICK*)
        {raw-pos-y :d, raw-v-y :v} (physics/d-and-v-given-desired-v
                                     pos-y
                                     v-y
                                     desired-v-y
                                     max-accel-y
                                     *GAME-SECONDS-PER-TICK*)
        hit-left?   (<= raw-pos-x 0.0)
        hit-right?  (>= raw-pos-x ROBOT-RANGE-X)
        hit-top?    (<= raw-pos-y 0.0)
        hit-bottom? (>= raw-pos-y ROBOT-RANGE-Y)
        new-touching (cond-> #{}
                       hit-left?   (conj :left)
                       hit-right?  (conj :right)
                       hit-top?    (conj :top)
                       hit-bottom? (conj :bottom))
        old-touching (or touching-walls #{})
        newly-hit-x? (and (or hit-left? hit-right?)
                          (not (or (:left old-touching) (:right old-touching))))
        newly-hit-y? (and (or hit-top? hit-bottom?)
                          (not (or (:top old-touching) (:bottom old-touching))))
        damage-delta (+ (if newly-hit-x? (wall-damage raw-v-x) 0.0)
                        (if newly-hit-y? (wall-damage raw-v-y) 0.0))]
    (merge robot {:pos-x (max 0.0 (min ROBOT-RANGE-X raw-pos-x))
                  :pos-y (max 0.0 (min ROBOT-RANGE-Y raw-pos-y))
                  :v-x   (if (or hit-left? hit-right?) 0.0 raw-v-x)
                  :v-y   (if (or hit-top? hit-bottom?) 0.0 raw-v-y)
                  :touching-walls new-touching
                  :damage (- damage damage-delta)})))

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
        abs-dist-x (rw-abs dist-x)
        abs-dist-y (rw-abs dist-y)
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
                                        (rw-copy-sign min-dist dist-x)))
                        :y (assoc 
                             actor 
                             :damage (dec (:damage actor))
                             :v-y (:v-y target)
                             :pos-y (- (:pos-y target) 
                                       (rw-copy-sign min-dist dist-y))))
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
  after the robot has taken its turn."
  [{robot-idx :idx :as robot} world]
  (if (not (:alive? robot))
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

