(ns robotwar.physics)

; trig functions

(defn deg->rad
  [angle]
  (* angle (/ Math/PI 180)))

(defn decompose-angle
  [angle-in-degrees]
  (let [angle (deg->rad angle-in-degrees)]
    {:x (Math/cos angle)
     :y (Math/sin angle)})) 

; classical mechanics functions

(defn time-to-reach-desired-v
  [vi vf a]
  (let [v-diff (- vf vi)]
    (if (zero? v-diff)
      0.0
      (double (/ v-diff a)))))

(defn d-with-constant-a
  [d vi a t]
  (+ d (* vi t) (/ (* a (Math/pow t 2)) 2)))

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
