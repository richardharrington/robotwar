(ns robotwar.app
  (:require [clojure.string :as str]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*]]
            [robotwar.world :as world]))

(def manifest-url "/programs/programs.json")
(def programs-base-url "/programs")
(def max-elapsed-ms 250)

(defonce state
  (atom {:manifest nil
         :running? false
         :world nil
         :tick-count 0
         :fast-forward 1
         :tick-duration-ms (* *GAME-SECONDS-PER-TICK* 1000)
         :accumulator-ms 0
         :last-frame-time nil
         :raf-id nil}))

(defn fetch-json [url]
  (-> (js/fetch url)
      (.then (fn [resp] (.json resp)))
      (.then (fn [data] (js->clj data :keywordize-keys true)))))

(defn fetch-text [url]
  (-> (js/fetch url)
      (.then (fn [resp] (.text resp)))))

(defn log-world-tick [tick-count {:keys [robots shells next-shell-id]}]
  (.log js/console
        (clj->js
         {:tick tick-count
          :robots (mapv (fn [{:keys [idx pos-x pos-y damage aim]}]
                          {:idx idx
                           :x (.toFixed pos-x 1)
                           :y (.toFixed pos-y 1)
                           :damage damage
                           :aim aim})
                        robots)
          :shell-count (count shells)
          :next-shell-id next-shell-id})))

(defn stop-game []
  (when-let [raf-id (:raf-id @state)]
    (js/cancelAnimationFrame raf-id))
  (swap! state assoc :running? false :raf-id nil :last-frame-time nil :accumulator-ms 0))

(defn loop-step [timestamp]
  (when (:running? @state)
    (let [{:keys [world tick-count tick-duration-ms accumulator-ms last-frame-time fast-forward]} @state
          elapsed-ms (if last-frame-time (- timestamp last-frame-time) 0)
          capped-elapsed-ms (min elapsed-ms max-elapsed-ms)
          game-elapsed-ms (* capped-elapsed-ms fast-forward)
          initial-accumulator-ms (+ accumulator-ms game-elapsed-ms)
          [next-world next-accumulator-ms next-tick-count]
          (loop [w world
                 a initial-accumulator-ms
                 n tick-count]
            (if (>= a tick-duration-ms)
              (let [w' (world/tick-combined-world w)
                    n' (inc n)]
                (log-world-tick n' w')
                (recur w' (- a tick-duration-ms) n'))
              [w a n]))]
      (swap! state assoc
             :world next-world
             :tick-count next-tick-count
             :accumulator-ms next-accumulator-ms
             :last-frame-time timestamp)
      (swap! state assoc :raf-id (js/requestAnimationFrame loop-step)))))

(defn ^:export start-game [program-names]
  (let [selected-names (->> program-names (remove str/blank?) vec)]
    (stop-game)
    (.log js/console "Starting CLJS game with programs:" (clj->js selected-names))
    (-> (js/Promise.all
         (clj->js
          (map (fn [program-name]
                 (fetch-text (str programs-base-url "/" program-name ".rw")))
               selected-names)))
        (.then (fn [programs]
                 (let [programs-clj (js->clj programs)
                       initial-world (world/init-world programs-clj)]
                   (swap! state assoc
                          :world initial-world
                          :tick-count 0
                          :accumulator-ms 0
                          :last-frame-time nil
                          :running? true)
                   (swap! state assoc :raf-id (js/requestAnimationFrame loop-step))
                   (.log js/console "CLJS game loop started."))))
        (.catch (fn [err]
                  (.error js/console "Failed to start CLJS game:" err))))))

(defn load-manifest! []
  (-> (fetch-json manifest-url)
      (.then (fn [manifest]
               (swap! state assoc :manifest manifest)
               (.log js/console "Loaded program manifest:" (clj->js (:programs manifest)))))
      (.catch (fn [err]
                (.error js/console "Failed to load manifest:" err)))))

(defn ^:export init []
  (.log js/console "RobotWar CLJS booted.")
  (load-manifest!))
