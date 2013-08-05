(ns robotwar.robot
  (:use [clojure.string :only [join]]
        (robotwar brain game-lexicon)))

(defn init-register 
  "takes a reg-name and a robot-idx (needed to locate the register in the world),
  as well as a read-func, a write-func, and a val. The read-func and write-func
  take the same set of arguments as get-in and assoc-in, respectively.
  They are meant to be invoked from inside of interface functions which pass in all
  the parameters except the vector path to the register val, which is provided by 
  the let clojure."
  [reg-name robot-idx read-func write-func val]
  (let [path-to-val [:robots robot-idx :register reg-name :val]]
    {reg-name {:read (fn [world]
                       (read-func world path-to-val))
               :write (fn [world data]
                        (write-func world path-to-val data))
               :val val}}))

(defn default-register 
  "takes a reg-name and robot-idx, and returns a register with initial :val 0,
  whose read function returns :val and whose write function returns a world
  with its data argument pushed to :val" 
  [reg-name robot-idx]
  (init-register reg-name robot-idx get-in assoc-in 0))

(defn init-robot
  [idx pos-x pos-y src-code]
  {:idx idx
   :pos-x pos-x
   :pos-y pos-y
   :veloc-x 0
   :veloc-y 0
   :accel-x 0
   :accel-y 0
   :damage 100
   ;TODO: make some custom registers
   :registers (into {} (for [reg-name robotwar.game-lexicon/reg-names]
                         (default-register reg-name idx)))
   :brain (robotwar.brain/init-brain src-code robotwar.game-lexicon/reg-names)})

(defn step-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn"
  [robot world]
  (if (<= (:damage robot) 0)
    world
    (robotwar.brain/step-brain robot world)))

