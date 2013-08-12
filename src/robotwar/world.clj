(ns robotwar.world
  (:use [clojure.string :only [join]])
  (:require [robotwar.robot :as robot]))

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
                                 {:icon (str idx)
                                  :pos-x (rand-int width)
                                  :pos-y (rand-int height)
                                  :aim 0.0
                                  :damage 0.0}))
                             programs))
   :robot-idx 0})

(defn tick-world
  "TODO: fill this out quite a bit. Dealing with shells, for instance.
  We might want to change this to a system where we count by whole rounds
  (where each robot gets to go) rather than just a stream of worlds, one for 
  each robot. Because otherwise, do we step the shells after every 
  single robot has their turn?"
  [{:keys [robots robot-idx] :as world} tick-duration]
  (assoc (robot/step-robot (robots robot-idx) world tick-duration)
         :robot-idx
         (mod (inc robot-idx) (count robots))))

(defn iterate-worlds
  "convenience function for creating a sequence of worlds"
  [world tick-duration]
  (iterate #(tick-world % tick-duration) world))
  
(defn get-world
  "convenience function for identifying a world in a sequence of worlds
  by its round idx (where one round means all the robots have stepped)
  and its robot idx."
  [round-idx robot-idx worlds]
  (let [num-robots (count (:robots (nth worlds 0)))
        world-idx (+ (* round-idx num-robots) robot-idx)]
    (nth worlds world-idx)))

(defn near-point [[pos-x pos-y] [x y]] 
  (and (= (Math/round pos-x) x)
       (= (Math/round pos-y) y)))

(defn arena-text-grid
  "outputs the arena, with borders"
  [{:keys [width height robots]} output-width output-height]
  (let [horiz-border-char "-"
        vert-border-char "+"
        header-footer (apply str (repeat (+ output-width 2) horiz-border-char))
        scale-x #(* % (/ output-width width))
        scale-y #(* % (/ output-height height))
        field (for [y (range output-height), x (range output-width)]
                (or (some (fn [{:keys [icon pos-x pos-y]}]
                        (when (near-point [(scale-x pos-x) (scale-y pos-y)] [x y])
                          icon))
                      robots)
                    " "))]
    (str header-footer
         "\n" 
         (join "\n" (map #(join (apply str %) (repeat 2 vert-border-char))
                         (partition output-width field))) 
         "\n" 
         header-footer)))
