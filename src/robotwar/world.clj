(ns robotwar.world
  (:use [clojure.string :only [join]]
        (robotwar foundry robot game-lexicon)))

; TODO: Write init-register function, in robot probably, and init those 
; X and Y registers down below.

(defn init-world
  "initialize all the variables for a robot world"
  [width height programs]
  {:width width
   :height height
   :shells []
   :robots (vec (map-indexed (fn [idx program]
                          {:internal-state (init-internal-state program reg-names 
                                                             {"X" (rand-int width)
                                                              "Y" (rand-int height)}) 
                           :icon (str idx)}) 
                        programs))})

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
