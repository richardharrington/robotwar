(ns robotwar.world
  (:use [clojure.string :only [join]]
        [robotwar.constants])
  (:require [clj-time.core :as time]
            [clj-time.periodic :as periodic]
            [robotwar.robot :as robot]))

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
  (reduce (fn [{robots :robots :as world} robot-idx]
            (robot/step-robot (robots robot-idx) world))
          starting-world
          (range (count (:robots starting-world)))))

(def build-combined-worlds (partial iterate tick-combined-world))
