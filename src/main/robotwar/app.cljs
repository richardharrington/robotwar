(ns robotwar.app
  (:require [clojure.string :as str]
            [robotwar.canvas :as canvas]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*]]
            [robotwar.world :as world]))

(def manifest-url "/programs/programs-live.json")
(def programs-base-url "/programs")
(def max-elapsed-ms 250)
(def max-fast-forward 40)
(def starting-fast-forward 15)
(def max-program-count 5)

(defonce state
  (atom {:manifest nil
         :running? false
         :world nil
         :previous-world nil
         :tick-count 0
         :fast-forward starting-fast-forward
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

(defonce sound-state (atom {:shell-release nil :idx 0}))

(defn init-sounds! []
  (let [supports-ogg? (not= "" (.canPlayType (js/Audio.) "audio/ogg"))
        src (if supports-ogg? "audio/trprsht1.ogg" "audio/trprsht1.mp3")
        els (vec (repeatedly 40 #(js/Audio. src)))]
    (swap! sound-state assoc :shell-release els :idx 0)))

(defn play-shell-release! []
  (let [{:keys [shell-release idx]} @sound-state]
    (when (seq shell-release)
      (-> (.play (nth shell-release idx))
          (.catch (fn [_] nil)))
      (swap! sound-state assoc :idx (mod (inc idx) (count shell-release))))))

(defn on-keydown [event]
  (let [k (.-which event)]
    (cond
      (= k 37) (swap! state update :fast-forward #(max 1 (dec %)))
      (= k 39) (swap! state update :fast-forward #(min max-fast-forward (inc %)))
      :else nil)))

(defn stop-game []
  (when-let [raf-id (:raf-id @state)]
    (js/cancelAnimationFrame raf-id))
  (swap! state assoc :running? false :raf-id nil :last-frame-time nil :accumulator-ms 0 :previous-world nil))

(defn loop-step [timestamp]
  (when (:running? @state)
    (let [{:keys [world tick-count tick-duration-ms accumulator-ms last-frame-time fast-forward previous-world]} @state
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
             :previous-world (or world previous-world)
             :world next-world
             :tick-count next-tick-count
             :accumulator-ms next-accumulator-ms
             :last-frame-time timestamp)
      (canvas/animate-world! (or previous-world world) next-world)
      (when (not= (:next-shell-id (or previous-world world)) (:next-shell-id next-world))
        (play-shell-release!))
      (if (:result next-world)
        (swap! state assoc :running? false)
        (swap! state assoc :raf-id (js/requestAnimationFrame loop-step))))))

(defn parse-program-names [value]
  (->> (str/split (or value "") #"[\s,]+")
       (remove str/blank?)
       vec))

(defn valid-program-names [program-names]
  (let [available (set (get-in @state [:manifest :programs]))]
    (->> program-names
         (filter #(contains? available %))
         (take max-program-count)
         vec)))

(defn start-transition! [input-el]
  (when-let [instruction-box (.querySelector js/document ".instruction-box")]
    (set! (.. instruction-box -style -height) "0"))
  (js/setTimeout
   (fn []
     (when-let [canvas-el (.getElementById js/document "canvas")]
       (set! (.. canvas-el -style -opacity) "1")))
   500)
  (.blur input-el))

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
                       initial-world (world/init-world programs-clj)
                       world-with-names (assoc initial-world :program-names (vec selected-names))]
                   (swap! state assoc
                          :world world-with-names
                          :previous-world world-with-names
                          :tick-count 0
                          :accumulator-ms 0
                          :last-frame-time nil
                          :running? true)
                   (canvas/animate-world! world-with-names world-with-names)
                   (swap! state assoc :raf-id (js/requestAnimationFrame loop-step))
                   (.log js/console "CLJS game loop started."))))
        (.catch (fn [err]
                  (.error js/console "Failed to start CLJS game:" err))))))

(defn on-program-input-keydown [event]
  (when (= 13 (.-which event))
    (.stopPropagation event)
    (.preventDefault event)
    (let [raw-names (parse-program-names (.. event -target -value))
          program-names (if (:manifest @state) (valid-program-names raw-names) raw-names)]
      (start-transition! (.-target event))
      (start-game program-names))))

(defn wire-input! []
  (when-let [input-el (.getElementById js/document "programsInput")]
    (.addEventListener input-el "keydown" on-program-input-keydown)))

(defn render-program-names! [manifest]
  (when-let [names-el (.getElementById js/document "programNames")]
    (set! (.-textContent names-el) (str/join ", " (:programs manifest))))
  (set! (.. js/document -body -style -display) "block"))

(defn load-manifest! []
  (-> (fetch-json manifest-url)
      (.then (fn [manifest]
               (swap! state assoc :manifest manifest)
               (render-program-names! manifest)
               (.log js/console "Loaded program manifest:" (clj->js (:programs manifest)))))
      (.catch (fn [err]
                (.error js/console "Failed to load manifest:" err)))))

(defn ^:export init []
  (.log js/console "RobotWar CLJS booted.")
  (init-sounds!)
  (.addEventListener js/document "keydown" on-keydown)
  (wire-input!)
  (load-manifest!))
