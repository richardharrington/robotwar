(ns robotwar.browser
  (:use [robotwar.constants])
  (:require [robotwar.physics :as physics]))

(defn worlds-for-browser
  "builds a sequence of worlds with the robots' brains
  removed, for more compact transmission by json.
  Fast-forward factor will be dynamically added by animation
  function in browser."
  [worlds]
  (letfn [(select-robot-keys [robot]
            (select-keys robot [:idx
                                :pos-x 
                                :pos-y 
                                :aim
                                :damage
                                :shot-timer]))
          (select-shell-keys [shell]
            (select-keys shell [:id
                                :pos-x
                                :pos-y
                                :exploded]))
          (three-sigs-map [m]
            (zipmap (keys m)
                    (map #(if (float? %)
                            (physics/three-sigs %)
                            %) 
                         (vals m))))
          (compact-robots [world]
            (update-in 
              world 
              [:robots]
              #(mapv (comp three-sigs-map select-robot-keys) %)))
          (compact-shells [world]
            (update-in
              world
              [:shells :shell-map]
              #(map (comp three-sigs-map select-shell-keys) %)))]
    (map (comp compact-shells compact-robots) worlds)))
