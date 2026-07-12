(ns robotwar.physics)

(defn rw-round [x]
  #?(:clj (Math/round x)
     :cljs (js/Math.round x)))

(defn rw-to-radians [x]
  #?(:clj (Math/toRadians x)
     :cljs (* x (/ js/Math.PI 180))))

(defn rw-cos [x]
  #?(:clj (Math/cos x)
     :cljs (js/Math.cos x)))

(defn rw-sin [x]
  #?(:clj (Math/sin x)
     :cljs (js/Math.sin x)))

(defn rw-pow [x y]
  #?(:clj (Math/pow x y)
     :cljs (js/Math.pow x y)))

(defn rw-sqrt [x]
  #?(:clj (Math/sqrt x)
     :cljs (js/Math.sqrt x)))

; precision functions

(defn three-sigs [x]
  (double (/ (rw-round (* x 1000)) 1000)))

; trig functions

(defn robotwar-deg->clojure-deg
  [angle]
  (- angle 90))

(defn decompose-angle
  [angle-in-degrees]
  (let [angle (rw-to-radians (robotwar-deg->clojure-deg angle-in-degrees))]
    {:x (rw-cos angle)
     :y (rw-sin angle)}))

; classical mechanics functions

(defn time-to-reach-desired-v
  [vi vf a]
  (let [v-diff (- vf vi)]
    (if (zero? v-diff)
      0.0
      (double (/ v-diff a)))))

(defn d-with-constant-a
  [d vi a t]
  (+ d (* vi t) (/ (* a (rw-pow t 2)) 2)))

(defn v-with-constant-a
  [vi a t]
  (+ vi (* a t)))

(defn d-and-v-given-desired-v
  "returns a map of distance and velocity at final position.
  the function deals with either of two cases:
  1) when the desired velocity is not reached during the
     given time interval, in which case it's just
     distance-with-constant-acceleration
  2) when we reach the desired velocity (or are already there)
     and then cruise the rest of the way"
  [d vi vf a t]
  (let [t' (time-to-reach-desired-v vi vf a)]
    (if (> t' t)
      {:d (d-with-constant-a d vi a t)
       :v (v-with-constant-a vi a t)}
      {:d (d-with-constant-a (d-with-constant-a d vi a t') vf 0.0 (- t t'))
       :v vf})))

; ray-casting helpers (used by the RADAR register)

(defn ray-disc-hit-distance
  "given a ray with origin (px, py) and unit direction (dx, dy), and a
  disc centered at (cx, cy) with radius r, return the distance from the
  origin to the disc's near-side entry point along the ray. Returns nil
  if the ray misses the disc entirely or the disc lies fully behind the
  origin. Returns 0.0 if the origin is inside the disc."
  [px py dx dy cx cy r]
  (let [vx (- cx px)
        vy (- cy py)
        tc (+ (* vx dx) (* vy dy))
        d2 (- (+ (* vx vx) (* vy vy)) (* tc tc))
        r2 (* r r)]
    (when (<= d2 r2)
      (let [offset (rw-sqrt (- r2 d2))
            t-near (- tc offset)
            t-far  (+ tc offset)]
        (cond
          (>= t-near 0.0) t-near
          (>= t-far 0.0)  0.0
          :else nil)))))

(defn ray-arena-exit-distance
  "given a ray with origin (px, py) and unit direction (dx, dy), return
  the distance to the point at which the ray exits the arena rectangle
  [0, range-x] × [0, range-y]. Assumes the origin lies inside the arena."
  [px py dx dy range-x range-y]
  (let [tx (cond
             (pos? dx) (/ (- range-x px) dx)
             (neg? dx) (/ (- px) dx)
             :else nil)
        ty (cond
             (pos? dy) (/ (- range-y py) dy)
             (neg? dy) (/ (- py) dy)
             :else nil)]
    (cond
      (and tx ty) (min tx ty)
      tx tx
      ty ty
      :else 0.0)))
