(ns robotwar.robot
  (:use [clojure.string :only [join]])
  (:require [robotwar.brain :as brain]
            [robotwar.game-lexicon :as game-lexicon]))

; TICK_DURATION is in seconds. 
; TODO: should be passed in from some higher level module, or a config module.
(def TICK_DURATION 1)

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
           ; INDEX
           (init-default-register "INDEX" robot-idx)

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

           ;X and Y and DAMAGE
           (init-read-only-register "X" robot-idx :pos-x (:pos-x attributes))
           (init-read-only-register "Y" robot-idx :pos-y (:pos-y attributes))
           (init-read-only-register "DAMAGE" robot-idx :damage (:damage attributes))])))

; REGISTERS DONE: "X" "Y" "DAMAGE" "RANDOM" "INDEX" "DATA" 
; REGISTERS TODO: "AIM" "SHOT" "RADAR" "SPEEDX" "SPEEDY" 

(defn init-robot
  [idx src-code attributes]
  {:idx idx
   :pos-x (:pos-x attributes)
   :pos-y (:pos-y attributes)
   :veloc-x 0
   :veloc-y 0
   :accel-x 0
   :accel-y 0
   :damage (:damage attributes)
   :registers (init-registers idx attributes)
   :brain (brain/init-brain src-code game-lexicon/reg-names)})

(defn step-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn.
  TODO: add a lot more stuff here that happens after the step-brain function,
  like moving the robot. Actually, that's the main thing."
  [robot world]
  (if (>= (:damage robot) 100)
    world
    (brain/step-brain robot world)))

