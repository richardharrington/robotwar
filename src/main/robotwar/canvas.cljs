(ns robotwar.canvas
  (:require [robotwar.constants :refer [BLAST-RADIUS ROBOT-RADIUS
                                        ROBOT-RANGE-X ROBOT-RANGE-Y]]))

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

(def max-edge-darkening
  "how much darker the body gradient's rim can get at maximum damage —
  a soft secondary cue; the center always stays the exact legend color"
  0.35)

(defn- damage-lost [damage]
  (/ (- 100 (max 0 (min 100 damage))) 100))

(defn edge-color
  "the rim color of the body's radial gradient: the palette color with
  lightness scaled down in proportion to damage lost"
  [idx damage]
  (let [{:keys [h s l]} (nth robot-colors-hsl idx)]
    (str "hsl(" h "," s "%," (* l (- 1 (* max-edge-darkening (damage-lost damage)))) "%)")))

(def damage-per-mark 20)

(defn damage-mark-count [damage]
  (js/Math.floor (/ (- 100 (max 0 (min 100 damage))) damage-per-mark)))

(defn damage-tier
  "escalation tier for the current mark count: 1 = dents, 2 = cracks,
  3 = cracks + blotches + silhouette notches. All marks render in the
  current tier's style (they upgrade in place at tier crossings)."
  [mark-count]
  (cond (>= mark-count 4) 3
        (>= mark-count 2) 2
        (pos? mark-count) 1
        :else 0))

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

;; Shell explosions follow the same wall-clock pattern: a warm fireball
;; disc that grows fast for the first grow-frac of its life, then fades
;; in place, plus a thin shockwave ring that outruns it.
(def ^:private shell-explosion-ms 500)
(def ^:private shell-explosion-grow-frac 0.35)

(defonce death-animations (atom {}))
(defonce shell-explosions (atom {}))

(defn animations-active? []
  (boolean (or (seq @death-animations) (seq @shell-explosions))))

(defn clear-animations! []
  (reset! death-animations {})
  (reset! shell-explosions {}))

(defn spawn-shell-explosion!
  "Register a shell blast at (x, y) in world meters, keyed by shell id."
  [shell-id x y]
  (swap! shell-explosions assoc shell-id
         {:x x :y y :age-ms 0 :last-ms nil}))

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

(defn- tick-ages
  "Advance every animation's age by wall-clock elapsed time and cull
  ones older than max-ms."
  [anims now max-ms]
  (into {}
        (keep (fn [[k anim]]
                (let [last-ms (or (:last-ms anim) now)
                      age (+ (:age-ms anim) (- now last-ms))]
                  (when (< age max-ms)
                    [k (assoc anim :age-ms age :last-ms now)]))))
        anims))

(defn- tick-animations! []
  (let [now (js/performance.now)]
    (swap! death-animations tick-ages now death-anim-ms)
    (swap! shell-explosions tick-ages now shell-explosion-ms)))

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
        blast-display-radius (scale-x BLAST-RADIUS)
        shell-display-radius (scale-x (* ROBOT-RADIUS 0.3))
        gun-display-length (scale-x (* ROBOT-RADIUS 1.4))
        gun-display-width (scale-x (* ROBOT-RADIUS 0.5))
        ctx (.getContext canvas "2d")
        ;; robot bodies are composed on this scratch canvas so tier-3
        ;; notches can erase through the silhouette (destination-out)
        ;; without punching holes in whatever the main canvas already
        ;; drew underneath (scorch marks, overlapping robots)
        sprite-size (* robot-display-radius 4)
        sprite-half (/ sprite-size 2)
        sprite-canvas (.createElement js/document "canvas")
        sctx (do (set! (.-width sprite-canvas) sprite-size)
                 (set! (.-height sprite-canvas) sprite-size)
                 (.getContext sprite-canvas "2d"))]
    (set! (.-lineCap ctx) "square")
    (set! (.-lineCap sctx) "square")
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
            (body-path [c shape x y]
              ;; the diamond's vertex radius is inflated for visual
              ;; parity — at equal radius it reads much smaller than the
              ;; square (half the area). Collision stays circle-circle
              ;; at ROBOT-RADIUS regardless.
              (let [r robot-display-radius
                    dr (* r 1.2)]
                (.beginPath c)
                (case shape
                  :square (.rect c (- x r) (- y r) (* r 2) (* r 2))
                  :circle (.arc c x y r 0 (* js/Math.PI 2) true)
                  :diamond (doto c
                             (.moveTo x (- y dr))
                             (.lineTo (+ x dr) y)
                             (.lineTo x (+ y dr))
                             (.lineTo (- x dr) y)
                             (.closePath)))))
            (edge-distance [shape angle]
              ;; distance from body center to the silhouette edge along
              ;; a ray at the given angle
              (let [r robot-display-radius
                    ca (js/Math.abs (js/Math.cos angle))
                    sa (js/Math.abs (js/Math.sin angle))]
                (case shape
                  :circle r
                  :square (/ r (max ca sa))
                  :diamond (/ (* r 1.2) (+ ca sa)))))
            (body-gradient [cx cy idx damage]
              ;; center is always the exact palette color (matching the
              ;; legend swatch); the rim darkens with damage, capped by
              ;; max-edge-darkening
              (let [grad (.createRadialGradient sctx cx cy 0 cx cy
                                                (* robot-display-radius 1.3))]
                (.addColorStop grad 0 (nth robot-colors idx))
                (.addColorStop grad 0.4 (nth robot-colors idx))
                (.addColorStop grad 1 (edge-color idx damage))
                grad))
            (crack-arm [c mx my base-angle seed segs]
              ;; one jagged arm wandering out from the mark point
              (.moveTo c mx my)
              (loop [i 0, px mx, py my, a base-angle]
                (when (< i segs)
                  (let [len (+ 3.5 (* 4 (rand01 (+ seed (* i 5.1)))))
                        a' (+ a (* 1.4 (- (rand01 (+ seed (* i 5.1) 2.2)) 0.5)))
                        nx (+ px (* len (js/Math.cos a')))
                        ny (+ py (* len (js/Math.sin a')))]
                    (.lineTo c nx ny)
                    (recur (inc i) nx ny a')))))
            (draw-damage-marks [c x y shape idx mark-count tier]
              ;; one mark per damage-per-mark points lost; geometry is a
              ;; pure function of (idx, mark-number) so existing marks
              ;; stay put as damage keeps dropping, and only their
              ;; rendering style changes when the tier escalates.
              ;; Caller clips to the body silhouette; mark centers also
              ;; stay within a per-shape offset bound.
              (let [max-offset (* robot-display-radius
                                  (case shape :square 0.7 :circle 0.6 :diamond 0.55))]
                (dotimes [k mark-count]
                  (let [seed (+ (* idx 97.13) (* k 17.77))
                        mx (+ x (* max-offset (- (* 2 (rand01 seed)) 1)))
                        my (+ y (* max-offset (- (* 2 (rand01 (+ seed 1.3))) 1)))
                        angle (* js/Math.PI (rand01 (+ seed 2.6)))]
                    (if (= tier 1)
                      ;; dents: short thick dashes
                      (let [half-len (+ 4 (* 3 (rand01 (+ seed 3.9))))
                            dx (* half-len (js/Math.cos angle))
                            dy (* half-len (js/Math.sin angle))]
                        (set! (.-strokeStyle c) "rgba(0,0,0,0.75)")
                        (set! (.-lineWidth c) 3)
                        (.beginPath c)
                        (.moveTo c (- mx dx) (- my dy))
                        (.lineTo c (+ mx dx) (+ my dy))
                        (.stroke c))
                      ;; cracks: two jagged arms radiating from the dent
                      ;; point, the second shorter and roughly opposite
                      (do
                        (set! (.-strokeStyle c) "rgba(0,0,0,0.8)")
                        (set! (.-lineWidth c) 2)
                        (.beginPath c)
                        (crack-arm c mx my angle seed 3)
                        (crack-arm c mx my (+ angle js/Math.PI 0.4) (+ seed 9.7) 2)
                        (.stroke c)))
                    ;; heavy damage: the latest marks also burn a dark
                    ;; blotch around their crack
                    (when (and (= tier 3) (>= k 3))
                      (let [br (* robot-display-radius
                                  (+ 0.3 (* 0.2 (rand01 (+ seed 4.4)))))
                            grad (.createRadialGradient c mx my 0 mx my br)]
                        (.addColorStop grad 0 "rgba(0,0,0,0.65)")
                        (.addColorStop grad 1 "rgba(0,0,0,0)")
                        (set! (.-fillStyle c) grad)
                        (.beginPath c)
                        (.arc c mx my br 0 (* js/Math.PI 2) true)
                        (.fill c)))))))
            (punch-notches [c x y shape idx mark-count]
              ;; tier 3 only: bite chunks out of the silhouette edge, one
              ;; per mark beyond the third. Erases sprite pixels only —
              ;; the main canvas underneath is untouched.
              (set! (.-globalCompositeOperation c) "destination-out")
              (dotimes [k mark-count]
                (when (>= k 3)
                  (let [seed (+ (* idx 97.13) (* k 17.77))
                        angle (* 2 js/Math.PI (rand01 (+ seed 5.8)))
                        d (edge-distance shape angle)
                        nr (* robot-display-radius
                              (+ 0.2 (* 0.12 (rand01 (+ seed 6.9)))))]
                    (.beginPath c)
                    (.arc c (+ x (* d (js/Math.cos angle)))
                          (+ y (* d (js/Math.sin angle)))
                          nr 0 (* js/Math.PI 2) true)
                    (.fill c))))
              (set! (.-globalCompositeOperation c) "source-over"))
            (draw-body-sprite [shape idx damage flash?]
              (let [mark-count (damage-mark-count damage)
                    tier (damage-tier mark-count)]
                (.clearRect sctx 0 0 sprite-size sprite-size)
                (body-path sctx shape sprite-half sprite-half)
                (set! (.-fillStyle sctx)
                      (if flash? "#fff" (body-gradient sprite-half sprite-half idx damage)))
                (.fill sctx)
                (when (pos? mark-count)
                  (.save sctx)
                  (body-path sctx shape sprite-half sprite-half)
                  (.clip sctx)
                  (draw-damage-marks sctx sprite-half sprite-half shape idx mark-count tier)
                  (.restore sctx)
                  (when (= tier 3)
                    (punch-notches sctx sprite-half sprite-half shape idx mark-count)))))
            (draw-robot [robot idx flash?]
              (let [x (offset-x (:pos-x robot))
                    y (offset-y (:pos-y robot))
                    shape (robot-shape idx)
                    color (if flash? "#fff" (nth robot-colors idx))]
                (draw-body-sprite shape idx (:damage robot) flash?)
                (.drawImage ctx sprite-canvas (- x sprite-half) (- y sprite-half))
                (stroke-circle x y (* robot-display-radius 0.6) (* gun-display-width 0.3))
                (draw-line-polar x y (:aim robot) gun-display-length gun-display-width color)))
            (draw-shell [shell]
              (fill-circle (offset-x (:pos-x shell))
                           (offset-y (:pos-y shell))
                           shell-display-radius
                           shell-color))
            (draw-shell-explosions []
              (doseq [[_ {:keys [x y age-ms]}] @shell-explosions]
                (let [cx (offset-x x)
                      cy (offset-y y)
                      t (/ age-ms shell-explosion-ms)
                      grow-t (min 1.0 (/ t shell-explosion-grow-frac))
                      ;; ease-out expansion; the disc's transparent rim
                      ;; peaks exactly at BLAST-RADIUS, so the graphic
                      ;; shows the true damage zone
                      radius (* blast-display-radius
                                (- 1 (let [u (- 1 grow-t)] (* u u))))
                      alpha (if (< t shell-explosion-grow-frac)
                              1.0
                              (- 1 (/ (- t shell-explosion-grow-frac)
                                      (- 1 shell-explosion-grow-frac))))]
                  (when (pos? radius)
                    (let [grad (.createRadialGradient ctx cx cy 0 cx cy radius)]
                      (.addColorStop grad 0 "rgba(255,255,255,1)")
                      (.addColorStop grad 0.25 "rgba(255,235,150,0.9)")
                      (.addColorStop grad 0.6 "rgba(255,140,40,0.65)")
                      (.addColorStop grad 1 "rgba(255,60,0,0)")
                      (set! (.-globalAlpha ctx) alpha)
                      (set! (.-fillStyle ctx) grad)
                      (.beginPath ctx)
                      (.arc ctx cx cy radius 0 (* 2 js/Math.PI) true)
                      (.fill ctx)))
                  ;; shockwave ring outrunning the disc — a decorative
                  ;; transient allowed to travel past BLAST-RADIUS
                  (set! (.-globalAlpha ctx) (- 1 t))
                  (set! (.-strokeStyle ctx) "rgba(255,200,120,0.8)")
                  (set! (.-lineWidth ctx) 2)
                  (.beginPath ctx)
                  (.arc ctx cx cy
                        (* blast-display-radius (+ 0.3 (* 1.4 t)))
                        0 (* 2 js/Math.PI) true)
                  (.stroke ctx)
                  (set! (.-globalAlpha ctx) 1))))
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
               ;; shells always detonate at their destination, so spawn
               ;; there rather than at the last live position
               (spawn-shell-explosion! id (:dest-x shell) (:dest-y shell)))))
         (doseq [[idx robot] (map-indexed vector (:robots current-world))]
           (when (:alive? robot)
             ;; hit flash (event cue) composes with the damage rendering
             ;; (state cue): a wounded robot still flashes white on a new hit
             (draw-robot robot idx
                         (not= (:damage (get-in previous-world [:robots idx]))
                               (:damage robot)))))
         (tick-animations!)
         (draw-shell-explosions)
         (draw-death-animations))})))

(defonce anim-instance (atom nil))

(defn animate-world! [previous-world current-world]
  (when-let [canvas (.getElementById js/document "canvas")]
    (when-not @anim-instance
      (reset! anim-instance (animation canvas)))
    (when current-world
      ((:animate-world @anim-instance) (or previous-world current-world) current-world))))
