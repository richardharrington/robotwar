(ns robotwar.app
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [robotwar.audio :as audio]
            [robotwar.canvas :as canvas]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*]]
            [robotwar.legend :as legend]
            [robotwar.victory :as victory]
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

(defn- new-contact?
  "true when any robot's contact set (e.g. :touching-walls or
  :colliding-with) gained a member this frame — the same transition the
  engine keys first-contact damage on."
  [prev-robots next-robots contact-key]
  (boolean
   (some (fn [[prev next]]
           (seq (set/difference (or (contact-key next) #{})
                                (or (contact-key prev) #{}))))
         (map vector prev-robots next-robots))))

(defn play-battle-sfx!
  "Diff the previous frame's world against the current one and fire a
  sound for each battle event: shell fired, shell exploded, robot-robot
  collision, wall crash, robot death. At most one sound per event type
  per frame — simultaneous events of the same type would just stack
  identical waveforms."
  [prev-world next-world]
  (when (and prev-world next-world (not (identical? prev-world next-world)))
    (let [prev-robots (:robots prev-world)
          next-robots (:robots next-world)
          next-shells (:shells next-world)]
      (when (not= (:next-shell-id prev-world) (:next-shell-id next-world))
        (audio/play! :shell-fire))
      (when (some (fn [[id _]] (not (contains? next-shells id)))
                  (:shells prev-world))
        (audio/play! :shell-explosion))
      (when (new-contact? prev-robots next-robots :colliding-with)
        (audio/play! :robot-collision))
      (when (new-contact? prev-robots next-robots :touching-walls)
        (audio/play! :wall-crash))
      (when (some (fn [[prev next]] (and (:alive? prev) (not (:alive? next))))
                  (map vector prev-robots next-robots))
        (audio/play! :robot-death)))))

(defn spawn-death-animations!
  "Spawn a particle burst for every robot whose :alive? flipped false
  this frame, at its last position, in its full-saturation color."
  [prev-world next-world]
  (when (and prev-world next-world (not (identical? prev-world next-world)))
    (doseq [[idx [prev next]] (map-indexed vector
                                           (map vector
                                                (:robots prev-world)
                                                (:robots next-world)))]
      (when (and (:alive? prev) (not (:alive? next)))
        (canvas/spawn-death-animation! idx
                                       (:pos-x next)
                                       (:pos-y next)
                                       (nth canvas/robot-colors idx))))))

(defn update-sound-toggle-button! []
  (when-let [el (.getElementById js/document "soundToggle")]
    (set! (.-textContent el) (if (audio/sound-enabled?) "🔊" "🔇"))))

(defn toggle-sound! []
  (audio/ensure-audio!)
  (audio/toggle-sound!)
  (update-sound-toggle-button!))

(defn set-program-input-enabled!
  "The programs input stays live during a battle (typing a new lineup
  restarts immediately), but is disabled while the victory overlay is
  up so a new game can't start underneath it."
  [enabled?]
  (when-let [input-el (.getElementById js/document "programsInput")]
    (set! (.-disabled input-el) (not enabled?))))

(defn show-input-error! [msg]
  (when-let [el (.getElementById js/document "inputError")]
    (set! (.-textContent el) msg)))

(defn clear-input-error! []
  (show-input-error! ""))

(defn stop-game []
  (when-let [raf-id (:raf-id @state)]
    (js/cancelAnimationFrame raf-id))
  (swap! state assoc :running? false :raf-id nil :last-frame-time nil :accumulator-ms 0 :previous-world nil))

(defn game-over? []
  (some? (get-in @state [:world :result])))

(defn restart-game! []
  (stop-game)
  (canvas/clear-animations!)
  (victory/hide!)
  (swap! state assoc :world nil :tick-count 0)
  (when-let [canvas-el (.getElementById js/document "canvas")]
    (set! (.. canvas-el -style -opacity) "0"))
  (when-let [legend-el (.getElementById js/document "legend")]
    (set! (.. legend-el -style -opacity) "0"))
  (when-let [toggle-el (.getElementById js/document "soundToggle")]
    (set! (.. toggle-el -style -opacity) "0"))
  (when-let [instruction-box (.querySelector js/document ".instruction-box")]
    (set! (.. instruction-box -style -height) ""))
  (clear-input-error!)
  (set-program-input-enabled! true)
  (when-let [input-el (.getElementById js/document "programsInput")]
    (.focus input-el)))

(defn on-keydown [event]
  (let [k (.-which event)
        typing? (= "INPUT" (.. event -target -tagName))]
    (cond
      (and (= k 13) (game-over?)) (restart-game!)
      (= k 37) (swap! state update :fast-forward #(max 1 (dec %)))
      (= k 39) (swap! state update :fast-forward #(min max-fast-forward (inc %)))
      (and (= k 52) (not typing?)) (toggle-sound!)
      :else nil)))

(defn on-canvas-click [_event]
  (when (game-over?)
    (restart-game!)))

(defn game-over-step
  "Post-victory wind-down: the engine no longer ticks, but death
  animations run in wall time and must play out. Keep redrawing until
  they're done, then bring up the victory overlay."
  [_timestamp]
  (let [{:keys [world]} @state]
    (when world
      (canvas/animate-world! world world)
      (if (canvas/animations-active?)
        (swap! state assoc :raf-id (js/requestAnimationFrame game-over-step))
        (do (set-program-input-enabled! false)
            (victory/show! world restart-game!))))))

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
      (spawn-death-animations! (or previous-world world) next-world)
      (canvas/animate-world! (or previous-world world) next-world)
      (legend/update-legend! next-world)
      (play-battle-sfx! (or previous-world world) next-world)
      (if (:result next-world)
        (do (swap! state assoc :running? false)
            (swap! state assoc :raf-id (js/requestAnimationFrame game-over-step)))
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
       (set! (.. canvas-el -style -opacity) "1"))
     (when-let [legend-el (.getElementById js/document "legend")]
       (set! (.. legend-el -style -opacity) "1"))
     (when-let [toggle-el (.getElementById js/document "soundToggle")]
       (set! (.. toggle-el -style -opacity) "1")))
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
                   (legend/build-legend! (vec selected-names))
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
    (audio/ensure-audio!)
    (let [raw-names (parse-program-names (.. event -target -value))
          program-names (if (:manifest @state) (valid-program-names raw-names) raw-names)]
      (if (< (count program-names) 2)
        (show-input-error! "Please enter at least 2 valid program names.")
        (do
          (clear-input-error!)
          (start-transition! (.-target event))
          (start-game program-names))))))

(defn wire-input! []
  (when-let [input-el (.getElementById js/document "programsInput")]
    (.addEventListener input-el "keydown" on-program-input-keydown))
  (when-let [canvas-el (.getElementById js/document "canvas")]
    (.addEventListener canvas-el "click" on-canvas-click))
  (when-let [toggle-el (.getElementById js/document "soundToggle")]
    (.addEventListener toggle-el "click" (fn [_] (toggle-sound!)))))

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
  (audio/preload!)
  (update-sound-toggle-button!)
  (.addEventListener js/document "keydown" on-keydown)
  (wire-input!)
  (load-manifest!))
