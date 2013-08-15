(ns robotwar.animate
  (:use [clojure.string :only [join]]
        [clojure.pprint :only [pprint]]
        [robotwar.constants])
  (:require [clj-time.core :as time]
            [clj-time.periodic :as periodic]))

(defn build-simulation-rounds [worlds fast-forward]
  (let [round-duration (/ *GAME-SECONDS-PER-TICK* fast-forward)
        num-robots (count (:robots (nth worlds 0)))
        rounds (partition num-robots worlds)]
    ;(println round-duration num-robots (nth rounds 0))
    (map-indexed (fn [idx worlds]
                  {:worlds worlds
                   :idx idx
                   :timestamp (int (* idx round-duration 1000))})
                 rounds)))

(defn near-point [[pos-x pos-y] [x y]] 
  (and (= (int pos-x) x)
       (= (int pos-y) y)))

(defn arena-text-grid
  "outputs the arena, with borders"
  [{:keys [width height robots]} print-width print-height]
  (let [horiz-border-char "-"
        vert-border-char "+"
        header-footer (apply str (repeat (+ (* print-width 3) 2) horiz-border-char))
        scale-x #(* % (/ print-width width))
        scale-y #(* % (/ print-height height))
        field (for [y (range print-height), x (range print-width)]
                (or (some (fn [{:keys [idx pos-x pos-y]}]
                        (when (near-point [(scale-x pos-x) (scale-y pos-y)] [x y])
                          (str "(" idx ")")))
                      robots)
                    "   "))]
    (str header-footer
         "\n" 
         (join "\n" (map #(join (apply str %) (repeat 2 vert-border-char))
                         (partition print-width field))) 
         "\n" 
         header-footer)))

(defn display-robots-info [world]
  (doseq [robot-idx (range (count (:robots world)))]
    (println (apply format 
                    "%d: x %.1f, y %.1f, v-x %.1f, v-y %.1f, desired-v-x %.1f, desired-v-y %.1f" 
                    (map #(get-in world [:robots robot-idx %]) 
                         [:idx :pos-x :pos-y :v-x :v-y :desired-v-x :desired-v-y]))))) 

(defn animate
  "takes a simulation and animates it."
  [initial-simulation-rounds print-width print-height fps]
  (let [frame-period (time/millis (* (/ 1 fps) 1000))
        starting-instant (time/now)]
    (loop [[{:keys [worlds idx]} :as rounds] initial-simulation-rounds
           frame-start starting-instant]
      (doseq [world worlds]
        (println (arena-text-grid world print-width print-height))
        (display-robots-info world) 
        (println (format "Animation frame rate: %.1f frames per second", fps))
        (println "Round number:" idx)
        (println (format "Seconds elapsed in the game-world: %.1f", (* idx *GAME-SECONDS-PER-TICK*)))
        (println))

      (let [desired-next-frame-start (time/plus frame-start frame-period)
            this-instant (time/now)
            next-frame-start (if (time/after? this-instant desired-next-frame-start)
                               this-instant
                               (do
                                 (Thread/sleep (time/in-msecs (time/interval
                                                                this-instant
                                                                desired-next-frame-start)))
                                 desired-next-frame-start))
            animation-timestamp (time/in-msecs (time/interval
                                                 starting-instant
                                                 next-frame-start))]
        (recur (drop-while (fn [round]
                             (< (:timestamp round) animation-timestamp))
                           rounds)
               next-frame-start)))))
                           

