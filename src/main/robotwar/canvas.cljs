(ns robotwar.canvas
  (:require [clojure.string :as str]
            [robotwar.constants :refer [ROBOT-RADIUS ROBOT-RANGE-X ROBOT-RANGE-Y]]))

(def robot-colors ["#fa2d0b" "#0bfaf7" "#faf20b" "#e312f0" "#4567fb"])
(def shell-color "#ffffff")

(defn- hex->hsl [hex]
  (let [r (/ (js/parseInt (subs hex 1 3) 16) 255)
        g (/ (js/parseInt (subs hex 3 5) 16) 255)
        b (/ (js/parseInt (subs hex 5 7) 16) 255)
        mx (max r g b)
        mn (min r g b)
        l (/ (+ mx mn) 2)
        d (- mx mn)
        s (if (zero? d) 0 (/ d (- 1 (js/Math.abs (- (* 2 l) 1)))))
        h (cond
            (zero? d) 0
            (= mx r) (mod (/ (- g b) d) 6)
            (= mx g) (+ (/ (- b r) d) 2)
            :else (+ (/ (- r g) d) 4))]
    {:h (* 60 h) :s (* 100 s) :l (* 100 l)}))

(def robot-colors-hsl (mapv hex->hsl robot-colors))

(defn damage-color
  "The robot's display color: its palette hue with saturation scaled
  linearly by damage (100 = fully saturated, 0 = grey)."
  [idx damage]
  (let [{:keys [h s l]} (nth robot-colors-hsl idx)
        health (/ (max 0 (min 100 damage)) 100)]
    (str "hsl(" h "," (* s health) "%," l "%)")))

(def damage-per-mark 20)

(defn damage-mark-count [damage]
  (js/Math.floor (/ (- 100 (max 0 (min 100 damage))) damage-per-mark)))

(defn- rand01
  "Deterministic pseudo-random [0,1) from a numeric seed — the classic
  fract(sin(seed)·large-prime) hash. Marks must not re-roll frame to
  frame (§4.4), so all mark geometry derives from this."
  [seed]
  (let [x (* (js/Math.sin (+ 12.9898 (* seed 78.233))) 43758.5453)]
    (- x (js/Math.floor x))))

(defn degrees->radians [angle]
  (* angle (/ js/Math.PI 180)))

(defn rw-degrees->js-degrees [angle]
  (- angle 90))

(defn polar->cartesian [angle distance]
  (let [a (degrees->radians (rw-degrees->js-degrees angle))]
    {:x (* distance (js/Math.cos a))
     :y (* distance (js/Math.sin a))}))

(defn animation [canvas]
  (let [width (.-width canvas)
        height (.-height canvas)
        room-for-robots (* ROBOT-RADIUS 2)
        arena-width (+ ROBOT-RANGE-X room-for-robots)
        arena-height (+ ROBOT-RANGE-Y room-for-robots)
        scale-factor-x (/ width arena-width)
        scale-factor-y (/ height arena-height)
        scale-x #(js/Math.round (* % scale-factor-x))
        scale-y #(js/Math.round (* % scale-factor-y))
        offset-x #(scale-x (+ ROBOT-RADIUS %))
        offset-y #(scale-y (+ ROBOT-RADIUS %))
        robot-display-radius (scale-x ROBOT-RADIUS)
        shell-display-radius (scale-x (* ROBOT-RADIUS 0.3))
        gun-display-length (scale-x (* ROBOT-RADIUS 1.4))
        gun-display-width (scale-x (* ROBOT-RADIUS 0.5))
        ctx (.getContext canvas "2d")]
    (set! (.-lineCap ctx) "square")
    (letfn [(fill-circle [x y r color]
              (set! (.-fillStyle ctx) color)
              (.beginPath ctx)
              (.arc ctx x y r 0 (* js/Math.PI 2) true)
              (.fill ctx))
            (stroke-circle [x y r line-width]
              (set! (.-lineWidth ctx) line-width)
              (set! (.-strokeStyle ctx) "#000")
              (.beginPath ctx)
              (.arc ctx x y r 0 (* js/Math.PI 2) true)
              (.stroke ctx))
            (fill-square [x y size color]
              (set! (.-fillStyle ctx) color)
              (.beginPath ctx)
              (.rect ctx (- x (/ size 2)) (- y (/ size 2)) size size)
              (.fill ctx))
            (draw-line-polar [x y angle d line-width color]
              (let [{dx :x dy :y} (polar->cartesian angle d)]
                (set! (.-lineWidth ctx) line-width)
                (set! (.-strokeStyle ctx) color)
                (.beginPath ctx)
                (.moveTo ctx x y)
                (.lineTo ctx (+ x dx) (+ y dy))
                (.stroke ctx)))
            (draw-damage-marks [x y idx damage]
              ;; short dark line segments, one per damage-per-mark points
              ;; lost; geometry is a pure function of (idx, mark-number)
              ;; so existing marks stay put as damage keeps dropping
              (let [mark-count (damage-mark-count damage)
                    max-offset (* robot-display-radius 0.7)
                    half-len 4]
                (when (pos? mark-count)
                  (set! (.-strokeStyle ctx) "rgba(0,0,0,0.75)")
                  (set! (.-lineWidth ctx) 2)
                  (dotimes [k mark-count]
                    (let [seed (+ (* idx 97.13) (* k 17.77))
                          mx (+ x (* max-offset (- (* 2 (rand01 seed)) 1)))
                          my (+ y (* max-offset (- (* 2 (rand01 (+ seed 1.3))) 1)))
                          angle (* js/Math.PI (rand01 (+ seed 2.6)))
                          dx (* half-len (js/Math.cos angle))
                          dy (* half-len (js/Math.sin angle))]
                      (.beginPath ctx)
                      (.moveTo ctx (- mx dx) (- my dy))
                      (.lineTo ctx (+ mx dx) (+ my dy))
                      (.stroke ctx))))))
            (draw-robot [robot idx color]
              (let [x (offset-x (:pos-x robot))
                    y (offset-y (:pos-y robot))]
                (fill-square x y (* robot-display-radius 2) color)
                (draw-damage-marks x y idx (:damage robot))
                (stroke-circle x y (* robot-display-radius 0.6) (* gun-display-width 0.3))
                (draw-line-polar x y (:aim robot) gun-display-length gun-display-width color)))
            (draw-shell [shell]
              (fill-circle (offset-x (:pos-x shell))
                           (offset-y (:pos-y shell))
                           shell-display-radius
                           shell-color))
            (explode-shell [shell]
              (fill-circle (offset-x (:pos-x shell))
                           (offset-y (:pos-y shell))
                           (* shell-display-radius 10)
                           shell-color))]
      {:animate-world
       (fn [previous-world current-world]
         (.clearRect ctx 0 0 width height)
         (let [current-shells (:shells current-world)
               previous-shells (:shells previous-world)]
           (doseq [[id shell] previous-shells]
             (if (contains? current-shells id)
               (draw-shell shell)
               (explode-shell shell))))
         (doseq [[idx robot] (map-indexed vector (:robots current-world))]
           (when (:alive? robot)
             ;; hit flash (event cue) composes with desaturation (state
             ;; cue): a wounded robot still flashes white on a new hit
             (if (not= (:damage (get-in previous-world [:robots idx])) (:damage robot))
               (draw-robot robot idx "#fff")
               (draw-robot robot idx (damage-color idx (:damage robot))))))
         (when-let [result (:result current-world)]
           (let [program-names (:program-names current-world)]
             (if (:winner result)
               (let [text (or (get program-names (:winner result))
                              (str "ROBOT " (:winner result) " WINS"))
                     color (nth robot-colors (:winner result))]
                 (set! (.-font ctx) "48px 'Data 70', monospace")
                 (set! (.-textAlign ctx) "center")
                 (set! (.-textBaseline ctx) "middle")
                 (set! (.-fillStyle ctx) color)
                 (.fillText ctx text (/ width 2) (/ height 2)))
               (let [names (keep #(get program-names %) (:just-died current-world))
                     names-str (when (seq names) (str/join ", " names))]
                 (set! (.-font ctx) "48px 'Data 70', monospace")
                 (set! (.-textAlign ctx) "center")
                 (set! (.-textBaseline ctx) "middle")
                 (set! (.-fillStyle ctx) "#ffffff")
                 (.fillText ctx "TIE" (/ width 2) (/ height 2))
                 (when names-str
                   (set! (.-font ctx) "24px 'Data 70', monospace")
                   (.fillText ctx names-str (/ width 2) (+ (/ height 2) 50))))))))})))

(defonce anim-instance (atom nil))

(defn animate-world! [previous-world current-world]
  (when-let [canvas (.getElementById js/document "canvas")]
    (when-not @anim-instance
      (reset! anim-instance (animation canvas)))
    (when current-world
      ((:animate-world @anim-instance) (or previous-world current-world) current-world))))
