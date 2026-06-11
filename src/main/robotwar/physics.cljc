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
