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
        ticked-shells (zipmap (keys shells)
                              (map shell/tick-shell (vals shells)))

        ; TODO: Is this the most idiomatic way to map a hash-map, testing
        ; each value in the hash-map on a predicate and then returning another hash-map? 
        ; looks clunky.

        live-shells (into {} (remove #(:exploded (val %)) 
                                     ticked-shells))
        exploded-shells (into {} (filter #(:exploded (val %))
                                         ticked-shells))]
    ; TODO: make this a real let-binding, that determines
    ; which robots were damaged.
    (let [damaged-world ticked-robots-world]
      (assoc damaged-world :shells live-shells))))

(def build-combined-worlds (partial iterate tick-combined-world))
