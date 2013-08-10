(ns robotwar.core
  (:use [clojure.pprint])
  (:require [robotwar.test-programs :as test-programs]
            [robotwar.world :as world]
            [robotwar.register :as register]))

; this is a hacky place for messing with stuff. 

(def world 
  (world/init-world 256 256 [test-programs/multi-use-program]))
(def worlds (world/iterate-worlds world))

(def robots (:robots world))
(def robot (robots 0))
(def registers (:registers robot))
(def rr register/read-register)
(def wr register/write-register)

(defn rv [reg-name] (get-in registers [reg-name :val]))

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
