(ns robotwar.robot
  (:use [clojure.string :only [join]]
        [clojure.pprint :only [pprint]])
  (:require [robotwar.brain :as brain]
            [robotwar.game-lexicon :as game-lexicon]))

; TICK_DURATION is in seconds. MAX_ACCEL is in decimeters per second per second. 
; TODO: should be passed in from some higher level module, or a config module.
(def TICK_DURATION 1)
(def MAX_ACCEL 40)

(defn init-register 
  "takes a reg-name and a robot-idx (needed to locate the register in the world),
  as well as a read-func, a write-func, and a val. The read-func and write-func
  take the same set of arguments as get-in and assoc-in, respectively.
  They are meant to be invoked from inside of interface functions which pass in all
  the parameters except the vector path to the register val, which is provided by 
  the let clojure."
  [reg-name robot-idx read-func write-func val]
  (let [path-to-val [:robots robot-idx :registers reg-name :val]]
    {reg-name {:read (fn [world]
                       (read-func world path-to-val))
               :write (fn [world data]
                        (write-func world path-to-val data))
               :val val}}))

(defn get-robot [world path-to-val]
  (get-in world (take 2 path-to-val)))

(defn get-registers [world path-to-val]
  (get-in world (take 3 path-to-val)))

(defn init-default-register
  "takes a reg-name and robot-idx, and returns a register with initial :val 0,
  whose read function returns :val and whose write function returns a world
  with its data argument pushed to :val" 
  [reg-name robot-idx]
  (init-register reg-name robot-idx get-in assoc-in 0))

(defn init-read-only-register
  "returns a register which has no effect (i.e. returns the world it was given)
  when it is written to, but which returns a particular robot field when it is read."
  [reg-name robot-idx field-name val]
  (init-register reg-name robot-idx 
                 (fn [world path-to-val]
                   (field-name (get-robot world path-to-val))) 
                 (fn [world _ _] world) 
                 val))

(defn init-registers
  [robot-idx attributes]
  (let [storage-registers (into {} (for [reg-name game-lexicon/storage-reg-names]
                                     (init-default-register reg-name robot-idx)))]
    (into storage-registers
          [
           ; AIM, INDEX, SPEEDX and SPEEDY.
           ; AIM and INDEX's specialized behaviors are only when they're used by
           ; SHOT and DATA, respectively. In themselves, they're only default registers.
           ; Likewise, SPEEDX and SPEEDY are used later in step-robot to determine
           ; the appropriate acceleration, which may have to applied over several ticks.
           (init-default-register "AIM" robot-idx)
           (init-default-register "INDEX" robot-idx)
           (init-default-register "SPEEDX" robot-idx)
           (init-default-register "SPEEDY" robot-idx)
           
           ; DATA
           (letfn [(target-register [world path-to-val]
                     (let [registers (get-registers world path-to-val)
                           index-register (registers "INDEX")]
                       (registers (game-lexicon/reg-names (:val index-register)))))]
             (init-register "DATA" robot-idx
               (fn [world path-to-val]
                 (brain/read-register (target-register world path-to-val) world))
               (fn [world path-to-val data]
                 (brain/write-register (target-register world) world data))
               0))

           ; RANDOM
           (init-register "RANDOM" robot-idx
                          (fn [world path-to-val]
                            (rand-int (get-in world path-to-val)))
                          assoc-in
                          0)

           ; X and Y and DAMAGE
           (init-read-only-register "X" robot-idx :pos-x (:pos-x attributes))
           (init-read-only-register "Y" robot-idx :pos-y (:pos-y attributes))
           (init-read-only-register "DAMAGE" robot-idx :damage (:damage attributes))])))

           ; TODO: SHOT AND RADAR

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
   :damage (:damage attributes)
   :registers (init-registers idx attributes)
   :brain (brain/init-brain src-code game-lexicon/reg-names)})

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
          desired-v-x (brain/read-register 
                        (get-in new-robot [:registers "SPEEDX"]) 
                        new-world)
          desired-v-y (brain/read-register 
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

