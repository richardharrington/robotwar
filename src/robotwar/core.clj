(ns robotwar.core
  (:use [clojure.pprint])
  (:require [robotwar.assembler :as assembler]
            [robotwar.brain :as brain]
            [robotwar.robot :as robot]
            [robotwar.world :as world]
            [robotwar.game-lexicon :as game-lexicon]
            [robotwar.brain-test :as brain-test]))

; this is a hacky place for messing with stuff. currently imports 
; all the test data from brain-test.

(def world (nth brain-test/worlds 0))
(def robots (:robots world))
(def robot (robots 0))
(def registers (:registers robot))
(def rr brain/read-register)
(def wr brain/write-register)

(defn rv [reg-name] (get-in registers [reg-name :val]))



; ppt uses some of those variables to 
; pretty-print a robot-state with line numbers for the obj-code instructions, 
; and only the registers you want. Very convenient.
;
; it takes a world-tick number and a robot index number, and prettyprints a robot
; with line numbers for the obj-code instructions, and only the registers specified.
; (also it only prints the values of the registers, not the register-maps with
; their ugly full system-names of the read and write functions.) Very convenient.

(def get-robot (fn [worlds world-tick-idx robot-idx]
                 ((:robots (world/get-world 
                             world-tick-idx 
                             robot-idx 
                             brain-test/worlds)) 
                  robot-idx)))

(def ppt (fn [worlds world-tick-idx robot-idx & [reg-keys]]
           (let [{:keys [brain registers] :as robot} 
                 (get-robot worlds world-tick-idx robot-idx)]
             (pprint 
               (into robot 
                     {:brain (assoc-in 
                               brain 
                               [:obj-code :instrs]
                               (sort (zipmap (range) (get-in 
                                                       brain 
                                                       [:obj-code :instrs]))))
                      :registers (sort (into {} (for [[reg-name reg-map] 
                                                      (select-keys 
                                                        registers 
                                                        reg-keys)]
                                                  {reg-name (:val reg-map)})))})))))
