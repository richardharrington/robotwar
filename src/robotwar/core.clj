(ns robotwar.core
  (:use [clojure.pprint :only [pprint]])
  (:require [robotwar.test-programs :as test-programs]
            [robotwar.world :as world]
            [robotwar.register :as register]
            [robotwar.robot :as robot]
            [robotwar.terminal :as terminal]
            [robotwar.browser :as browser]))

; this is a hacky place for messing with stuff. 



(def progs 
  (repeat 3 (:moving-to-spot test-programs/programs)))
(def world
  (world/init-world progs)) 
(defn combined-worlds [] 
  (world/build-combined-worlds world))
(defn worlds-for-terminal-display [fast-forward] 
  (terminal/worlds-for-terminal (combined-worlds) fast-forward))
(defn make-it-so [fast-forward fps]
  (terminal/animate (worlds-for-terminal-display fast-forward) 25 25 fps))

(defn worlds-for-browser-display []
  (browser/worlds-for-browser (combined-worlds)))

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

(def pprint-robot-at-combined-world 
  (fn [combined-worlds world-idx robot-idx & reg-keys]
    (apply pprint-robot 
           (get-in (nth combined-worlds world-idx) [:robots robot-idx]) 
           reg-keys)))

(def ppr pprint-robot)
(def pprw pprint-robot-at-combined-world)
