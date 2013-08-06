(ns robotwar.world
  (:use [clojure.string :only [join]])
  (:require [robotwar.robot]))

(defn init-world
  "initialize all the variables for a robot world."
  [width height programs]
  {:width width
   :height height
   :shells []
   :robots (vec (map-indexed (fn [idx program]
                               (robotwar.robot/init-robot idx program {:pos-x (rand-int width)
                                                                       :pos-y (rand-int height)
                                                                       :damage 100}))
                             programs))
   :robot-idx 0})

(defn tick-world
  "TODO: fill this out quite a bit. Dealing with shells, for instance.
  We might want to change this to a system where we count by whole rounds
  (where each robot gets to go) rather than just a stream of worlds, one for 
  each robot. Because otherwise, do we step the shells after every 
  single robot has their turn?"
  [{:keys [robots robot-idx] :as world}]
  (assoc (robotwar.robot/step-robot (robots robot-idx) world)
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

(defn arena-text-grid
  "outputs the arena, with borders"
  [{:keys [width height robots]}]
  (let [horiz-border-char "-"
        vert-border-char "+"
        header-footer (apply str (repeat (+ width 2) horiz-border-char))
        field (for [y (range height), x (range width)]
                (some (fn [{{{robot-x "X" robot-y "Y"} :registers} :internal-state, icon :icon}]
                        (if (= [x y] [robot-x robot-y])
                          icon
                          " "))
                      robots))]
    (str header-footer
         "\n" 
         (join "\n" (map #(join (apply str %) (repeat 2 vert-border-char))
                         (partition width field))) 
         "\n" 
         header-footer)))
