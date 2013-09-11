(ns robotwar.world
  (:use [clojure.string :only [join]]
        [robotwar.constants]
        [clojure.pprint :as pprint])
  (:require [clj-time.core :as time]
            [clj-time.periodic :as periodic]
            [robotwar.robot :as robot]
            [robotwar.shell :as shell]))

(defn init-world
  "initialize all the variables for a robot world."
  [programs]
  {:shells {} 
   :next-shell-id 0
   :robots (vec (map-indexed (fn [idx program]
                               (robot/init-robot 
                                 idx 
                                 program 
                                 {:pos-x (rand ROBOT-RANGE-X)
                                  :pos-y (rand ROBOT-RANGE-Y)
                                  :aim 0.0
                                  :damage 100.0}))
                             programs))})

(defn tick-combined-world
  [starting-world]
  (let [{:keys [shells next-shell-id] :as ticked-robots-world} 
        (reduce (fn [{robots :robots :as world} robot-idx]
                  (robot/tick-robot (robots robot-idx) world))
                starting-world
                (range (count (:robots starting-world))))
        ticked-shells (into {} (map (fn [shell-map-entry]
                                      [(key shell-map-entry)
                                       (shell/tick-shell (val shell-map-entry))])
                                    shells))]
    ; TODO: make this a real let-binding, that determines
    ; which robots were damaged.
    (let [damaged-world ticked-robots-world]
      (pprint ticked-shells)
      (assoc damaged-world :shells ticked-shells))))

(def build-combined-worlds (partial iterate tick-combined-world))
