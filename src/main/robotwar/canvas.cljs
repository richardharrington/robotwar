(ns robotwar.canvas
  (:require [robotwar.constants :refer [ROBOT-RADIUS ROBOT-RANGE-X ROBOT-RANGE-Y]]))

(def robot-colors ["#fa2d0b" "#0bfaf7" "#faf20b" "#e312f0" "#4567fb"])
(def shell-color "#ffffff")

(def robot-shapes
  "body shape per robot, chosen by idx mod 3. Visual identity only —
  collision stays circle-circle in the engine regardless of shape."
  [:square :circle :diamond])

(defn robot-shape [idx]
  (nth robot-shapes (mod idx (count robot-shapes))))

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

;; ---------------------------------------------------------------------
;; Death animation (presentation state only — the engine's :alive? flag
;; stays authoritative). Spawned by app.cljs on the alive->dead
;; transition; ticked in wall-clock time inside animate-world, so it
;; runs at the same speed regardless of fast-forward.

(def ^:private death-particle-count 30)
(def ^:private death-flash-ms 150)
(def ^:private death-ring-ms 500)
(def ^:private death-anim-ms 900)

(defonce death-animations (atom {}))

(defn animations-active? []
  (boolean (seq @death-animations)))

(defn clear-animations! []
  (reset! death-animations {}))

(defn spawn-death-animation!
  "Register a death burst at (x, y) in world meters. Particle velocities
  are deterministic per (robot-idx, particle-number) — §4.4."
  [robot-idx x y color]
  (swap! death-animations assoc robot-idx
         {:x x
          :y y
          :color color
          :age-ms 0
          :last-ms nil
          :particles
          (vec (for [k (range death-particle-count)]
                 (let [seed (+ (* robot-idx 31.7) (* k 7.93))
                       angle (* 2 js/Math.PI (rand01 seed))
                       speed (+ 20 (* 60 (rand01 (+ seed 1.7))))] ; m/s
                   {:vx (* speed (js/Math.cos angle))
                    :vy (* speed (js/Math.sin angle))
                    :max-age-ms (+ 400 (* 500 (rand01 (+ seed 3.1))))
                    :color (if (zero? (mod k 3)) "#ffffff" color)})))}))

(defn- tick-animations!
  "Advance every animation's age by wall-clock elapsed time and cull
  finished ones."
  []
  (let [now (js/performance.now)]
    (swap! death-animations
           (fn [anims]
             (into {}
                   (keep (fn [[idx anim]]
                           (let [last-ms (or (:last-ms anim) now)
                                 age (+ (:age-ms anim) (- now last-ms))]
                             (when (< age death-anim-ms)
                               [idx (assoc anim :age-ms age :last-ms now)])))
                         anims))))))

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
            (draw-line-polar [x y angle d line-width color]
              (let [{dx :x dy :y} (polar->cartesian angle d)]
                (set! (.-lineWidth ctx) line-width)
                (set! (.-strokeStyle ctx) color)
                (.beginPath ctx)
                (.moveTo ctx x y)
                (.lineTo ctx (+ x dx) (+ y dy))
                (.stroke ctx)))
            (body-path [shape x y]
              ;; the diamond's vertex radius is inflated for visual
              ;; parity — at equal radius it reads much smaller than the
              ;; square (half the area). Collision stays circle-circle
              ;; at ROBOT-RADIUS regardless.
              (let [r robot-display-radius
                    dr (* r 1.2)]
                (.beginPath ctx)
                (case shape
                  :square (.rect ctx (- x r) (- y r) (* r 2) (* r 2))
                  :circle (.arc ctx x y r 0 (* js/Math.PI 2) true)
                  :diamond (doto ctx
                             (.moveTo x (- y dr))
                             (.lineTo (+ x dr) y)
                             (.lineTo x (+ y dr))
                             (.lineTo (- x dr) y)
                             (.closePath)))))
            (fill-body [shape x y color]
              (set! (.-fillStyle ctx) color)
              (body-path shape x y)
              (.fill ctx))
            (draw-damage-marks [shape x y idx damage]
              ;; short dark line segments, one per damage-per-mark points
              ;; lost; geometry is a pure function of (idx, mark-number)
              ;; so existing marks stay put as damage keeps dropping.
              ;; Clipped to the body silhouette; mark centers also stay
              ;; within a per-shape offset bound so few get clipped away.
              (let [mark-count (damage-mark-count damage)
                    max-offset (* robot-display-radius
                                  (case shape :square 0.7 :circle 0.6 :diamond 0.55))
                    half-len 4]
                (when (pos? mark-count)
                  (.save ctx)
                  (body-path shape x y)
                  (.clip ctx)
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
                      (.stroke ctx)))
                  (.restore ctx))))
            (draw-robot [robot idx color]
              (let [x (offset-x (:pos-x robot))
                    y (offset-y (:pos-y robot))
                    shape (robot-shape idx)]
                (fill-body shape x y color)
                (draw-damage-marks shape x y idx (:damage robot))
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
                           shell-color))
            (draw-death-animations []
              (doseq [[_ {:keys [x y color age-ms particles]}] @death-animations]
                (let [cx (offset-x x)
                      cy (offset-y y)]
                  ;; white flash: covers the vanished body for the first
                  ;; beat so the burst visibly grows out of the robot
                  (when (< age-ms death-flash-ms)
                    (let [t (/ age-ms death-flash-ms)]
                      (set! (.-globalAlpha ctx) (- 1 t))
                      (fill-circle cx cy
                                   (* robot-display-radius (+ 1 (* 0.8 t)))
                                   "#ffffff")))
                  ;; inner ring: same expand-and-fade language as
                  ;; explode-shell, in the dead robot's color
                  (when (< age-ms death-ring-ms)
                    (let [t (/ age-ms death-ring-ms)]
                      (set! (.-globalAlpha ctx) (- 1 t))
                      (set! (.-strokeStyle ctx) color)
                      (set! (.-lineWidth ctx) 3)
                      (.beginPath ctx)
                      (.arc ctx cx cy
                            (* robot-display-radius (+ 1 (* 2.5 t)))
                            0 (* 2 js/Math.PI) true)
                      (.stroke ctx)))
                  ;; sparks: position is a pure function of age —
                  ;; outward velocity with ease-out deceleration; alpha
                  ;; fades quadratically (bright early, gone by max-age)
                  ;; and size shrinks from 3px to 1px
                  (doseq [{:keys [vx vy max-age-ms] pcolor :color} particles]
                    (when (< age-ms max-age-ms)
                      (let [t (/ age-ms max-age-ms)
                            secs (/ age-ms 1000)
                            ease (- 1 (* 0.5 t))]
                        (set! (.-globalAlpha ctx) (- 1 (* t t)))
                        (fill-circle (offset-x (+ x (* vx secs ease)))
                                     (offset-y (+ y (* vy secs ease)))
                                     (- 3 (* 2 t)) pcolor))))
                  (set! (.-globalAlpha ctx) 1))))]
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
         (tick-animations!)
         (draw-death-animations))})))

(defonce anim-instance (atom nil))

(defn animate-world! [previous-world current-world]
  (when-let [canvas (.getElementById js/document "canvas")]
    (when-not @anim-instance
      (reset! anim-instance (animation canvas)))
    (when current-world
      ((:animate-world @anim-instance) (or previous-world current-world) current-world))))
