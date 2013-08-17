(ns robotwar.world
  (:use [clojure.string :only [join]])
  (:require [clj-time.core :as time]
            [clj-time.periodic :as periodic]
            [robotwar.robot :as robot]))

(defn init-world
  "initialize all the variables for a robot world."
  [width height programs]
  {:width width
   :height height
   :shells []
   :robots (vec (map-indexed (fn [idx program]
                               (robot/init-robot 
                                 idx 
                                 program 
                                 {:pos-x (rand width)
                                  :pos-y (rand height)
                                  :aim 0.0
                                  :damage 100.0}))
                             programs))
   :robot-idx 0})

(defn tick-world
  "TODO: fill this out quite a bit. Dealing with shells, for instance.
  We might want to change this to a system where we count by whole rounds
  (where each robot gets to go) rather than just a stream of worlds, one for 
  each robot. Because otherwise, do we step the shells after every 
  single robot has their turn?"
  [{:keys [robots robot-idx] :as world}]
  (assoc (robot/step-robot (robots robot-idx) world)
         :robot-idx
         (mod (inc robot-idx) (count robots))))

(defn get-world
  "convenience function for identifying a world in a sequence of worlds
  by its round idx (where one round means all the robots have stepped)
  and its robot idx."
  [round-idx robot-idx worlds]
  (let [num-robots (count (:robots (nth worlds 0)))
        world-idx (+ (* round-idx num-robots) robot-idx)]
    (nth worlds world-idx)))
