(ns robotwar.victory
  "DOM victory overlay: winner (or TIE), a minimal leaderboard ordered
  winner-first then by ticks survived, and a Play Again button that
  triggers the Tier 1 restart flow. Rendered into the static
  #victoryOverlay div positioned over the canvas."
  (:require [robotwar.canvas :as canvas]))

(defn- overlay-el []
  (.getElementById js/document "victoryOverlay"))

(defn- el
  ([tag class] (el tag class nil))
  ([tag class text]
   (let [e (.createElement js/document tag)]
     (set! (.-className e) class)
     (when text (set! (.-textContent e) text))
     e)))

(defn- ticks-survived [robot final-tick]
  (or (:died-at-tick robot) final-tick))

(defn- leaderboard-rows [{:keys [robots program-names tick result]}]
  (let [winner-idx (:winner result)]
    (sort-by (fn [robot]
               [(if (= (:idx robot) winner-idx) 0 1)
                (- (ticks-survived robot tick))])
             robots)))

(defn- build-row [robot program-names final-tick]
  (let [{:keys [idx alive?]} robot
        row (el "div" (str "victory-row" (when-not alive? " dead")))
        swatch (el "div" "victory-swatch")
        name-el (el "div" "victory-name"
                    (or (get program-names idx) (str "ROBOT " idx)))
        status (el "div" "victory-status"
                   (str (if alive? "ALIVE" "DESTROYED")
                        " · " (ticks-survived robot final-tick) " TICKS"))]
    (set! (.. swatch -style -backgroundColor) (nth canvas/robot-colors idx))
    (.appendChild row swatch)
    (.appendChild row name-el)
    (.appendChild row status)
    row))

(defn hide! []
  (when-let [overlay (overlay-el)]
    (.remove (.-classList overlay) "visible")
    (set! (.-innerHTML overlay) "")))

(defn show!
  "Fill and reveal the overlay for a finished world. on-restart is
  called when the Play Again button is clicked."
  [world on-restart]
  (when-let [overlay (overlay-el)]
    (set! (.-innerHTML overlay) "")
    (let [{:keys [result program-names tick]} world
          winner-idx (:winner result)
          title (if winner-idx
                  (el "div" "victory-title"
                      (str (or (get program-names winner-idx)
                               (str "ROBOT " winner-idx)) " WINS"))
                  (el "div" "victory-title" "TIE"))
          board (el "div" "victory-leaderboard")
          button (el "button" "victory-button" "PLAY AGAIN")]
      (set! (.. title -style -color)
            (if winner-idx (nth canvas/robot-colors winner-idx) "#ffffff"))
      (doseq [robot (leaderboard-rows world)]
        (.appendChild board (build-row robot program-names tick)))
      (.addEventListener button "click" (fn [_] (on-restart)))
      (.appendChild overlay title)
      (.appendChild overlay board)
      (.appendChild overlay button)
      (.add (.-classList overlay) "visible"))))
