(ns robotwar.canvas
  (:require [robotwar.constants :refer [ROBOT-RADIUS ROBOT-RANGE-X ROBOT-RANGE-Y]]))

(def robot-colors ["#fa2d0b" "#0bfaf7" "#faf20b" "#e312f0" "#4567fb"])
(def shell-color "#ffffff")

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
            (draw-robot [robot color]
              (let [x (offset-x (:pos-x robot))
                    y (offset-y (:pos-y robot))]
                (fill-square x y (* robot-display-radius 2) color)
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
            (shell-map [shells]
              (cond
                (map? shells) shells
                (sequential? shells) (into {} (keep (fn [s] (when (and (map? s) (contains? s :id)) [(:id s) s])) shells))
                :else {}))]
      {:animate-world
       (fn [previous-world current-world]
         (.clearRect ctx 0 0 width height)
         (let [current-shells (shell-map (:shells current-world))
               previous-shells (shell-map (:shells previous-world))]
           (doseq [[id shell] previous-shells]
             (if (contains? current-shells id)
               (draw-shell shell)
               (explode-shell shell))))
         (doseq [[idx robot] (map-indexed vector (:robots current-world))]
           (if (not= (:damage (get-in previous-world [:robots idx])) (:damage robot))
             (draw-robot robot "#fff")
             (draw-robot robot (or (nth robot-colors idx nil) "#fff")))))})))

(defonce anim-instance (atom nil))

(defn animate-world! [previous-world current-world]
  (when-let [canvas (.getElementById js/document "canvas")]
    (when-not @anim-instance
      (reset! anim-instance (animation canvas)))
    (when current-world
      ((:animate-world @anim-instance) (or previous-world current-world) current-world))))
