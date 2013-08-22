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
  {:shells []
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
  (let [{shells :shells :as ticked-robots-world} (reduce (fn [{robots :robots :as world} robot-idx]
                                                           (robot/tick-robot (robots robot-idx) world))
                                                         starting-world
                                                         (range (count (:robots starting-world))))
        ticked-shells (map shell/tick-shell shells)
        live-shells (remove :exploded ticked-shells)
        exploded-shells (filter :exploded ticked-shells)]
    (println (count ticked-shells))
    ; TODO: make this a real let-binding, that determines
    ; which robots were damaged.
    (let [damaged-world ticked-robots-world]
      (assoc damaged-world :shells live-shells))))

(def build-combined-worlds (partial iterate tick-combined-world))
