(ns robotwar.world
  (:use [clojure.string :only [join]]
        (robotwar foundry robot)))

(defn init-world
  "initialize all the variables for a robot world"
  [width height programs]
  {:width width
   :height height
   :shells []
   :robots (map-indexed (fn [idx program]
                          {:internal-state (init-robot-state program 
                                                             {"X" (rand-int width)
                                                              "Y" (rand-int height)}) 
                           :external-state {:icon (str idx)}}) 
                        programs)})

(defn tick-world
  "TODO"
  [world-state])

(defn arena-text-grid
  "outputs the arena, with borders"
  [{:keys [width height robots]}]
  (let [horiz-border-char "-"
        vert-border-char "+"
        header-footer (apply str (repeat (+ width 2) horiz-border-char))
        field (for [y (range height), x (range width)]
                (some (fn [{{{robot-x "X" robot-y "Y"} :registers} :internal-state
                            {icon :icon} :external-state}]
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
