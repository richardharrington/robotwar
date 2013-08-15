(ns robotwar.core
  (:use [clojure.pprint :only [pprint]])
  (:require [robotwar.test-programs :as test-programs]
            [robotwar.world :as world]
            [robotwar.register :as register]
            [robotwar.robot :as robot]
            [robotwar.animate :as animate]))

; this is a hacky place for messing with stuff. 



(defn progs [] 
  (vals test-programs/programs))
(defn world [] 
  (world/init-world 256.0 256.0 (progs))) 
(defn worlds [] 
  (iterate world/tick-world (world)))
(defn simulation-rounds [] 
  (animate/build-simulation-rounds (worlds) 25))
(defn make-it-so []
  (animate/animate (simulation-rounds) 25 25 20.0))

(def rr register/read-register)
(def wr register/write-register)

; ppt uses some of those variables to 
; pretty-print a robot-state with line numbers for the obj-code instructions, 
; and only the registers you want. Very convenient.
;
; it takes a world-tick number and a robot index number, and prettyprints a robot
; with line numbers for the obj-code instructions, and only the registers specified.
; Very convenient.


(def pprint-robot 
  (fn [robot & reg-keys]
    (let [{{registers :registers, {instrs :instrs} :obj-code} :brain :as robot} robot
          registers-to-print (if reg-keys 
                               (select-keys registers reg-keys)
                               registers)]
      (pprint
        (assoc-in 
          (assoc-in robot [:brain :registers] registers-to-print) 
          [:brain :obj-code :instrs]
          (sort (zipmap (range) instrs))))))) 

(def pprint-robot-in-world 
  (fn [world robot-idx & reg-keys]
    (apply pprint-robot (get-in world [:robots robot-idx]) reg-keys)))

(def pprint-robot-at-world-tick 
  (fn [worlds world-tick-idx robot-idx & reg-keys]
    (apply pprint-robot-in-world 
           (world/get-world world-tick-idx robot-idx worlds) 
           robot-idx 
           reg-keys)))

(def ppr pprint-robot)
(def pprw pprint-robot-in-world)
(def pprwt pprint-robot-at-world-tick)
