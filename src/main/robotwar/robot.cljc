(ns robotwar.robot
  (:require [robotwar.constants :refer [*GAME-SECONDS-PER-TICK* MAX-ACCEL ROBOT-RADIUS
                                        ROBOT-RANGE-X ROBOT-RANGE-Y
                                        MAX-WALL-DAMAGE MAX-COLLISION-DAMAGE V-MAX]]
            [robotwar.brain :as brain]
            [robotwar.register :as register]
            [robotwar.physics :as physics]))

(defn rw-abs [x]
  #?(:clj (Math/abs x)
     :cljs (js/Math.abs x)))

(defn rw-copy-sign [magnitude sign]
  #?(:clj (Math/copySign magnitude sign)
     :cljs (* (rw-abs magnitude) (if (neg? sign) -1 1))))

(defn init-robot
  "takes a robot-idx, a program, and a robot attribute map and returns a robot.
  Distance and distance/time units are all in meters and meters per second."
  [idx src-code attributes]
  {:idx            idx
   :pos-x          (:pos-x attributes)
   :pos-y          (:pos-y attributes)
   :aim            (:aim attributes)
   :damage         (:damage attributes)
   :alive?         true
   :v-x            0.0
   :v-y            0.0
   :desired-v-x    0.0
   :desired-v-y    0.0
   :shot-timer     0.0
   :touching-walls #{}
   :colliding-with #{}
   :brain          (brain/init-brain src-code (register/init-registers idx))})

(defn update-robot
  "takes a robot index, a world, and a function, and returns a world
  with the robot updated by passing it through the function"
  [robot-idx world f]
  (update-in world [:robots robot-idx] f))

(defn update-shot-timer
  "takes a robot and returns one with the :shot-timer updated"
  [{shot-timer :shot-timer :as robot}]
  (merge robot {:shot-timer
                (max (- shot-timer *GAME-SECONDS-PER-TICK*) 0.0)}))

(defn- wall-damage
  "quadratic falloff in the perpendicular velocity component"
  [v-perp]
  (let [factor (/ (rw-abs v-perp) V-MAX)]
    (* MAX-WALL-DAMAGE factor factor)))

(defn move-robot
  "takes a robot and returns it, moved through space (with wall clamping,
  slide behavior, and first-contact wall damage applied).
  helper function for tick-robot."
  [{:keys [pos-x pos-y v-x v-y desired-v-x desired-v-y damage touching-walls] :as robot}]
  (let [max-accel-x (rw-copy-sign MAX-ACCEL desired-v-x)
        max-accel-y (rw-copy-sign MAX-ACCEL desired-v-y)
        {raw-pos-x :d, raw-v-x :v} (physics/d-and-v-given-desired-v
                                    pos-x
                                    v-x
                                    desired-v-x
                                    max-accel-x
                                    *GAME-SECONDS-PER-TICK*)
        {raw-pos-y :d, raw-v-y :v} (physics/d-and-v-given-desired-v
                                    pos-y
                                    v-y
                                    desired-v-y
                                    max-accel-y
                                    *GAME-SECONDS-PER-TICK*)
        hit-left?   (<= raw-pos-x 0.0)
        hit-right?  (>= raw-pos-x ROBOT-RANGE-X)
        hit-top?    (<= raw-pos-y 0.0)
        hit-bottom? (>= raw-pos-y ROBOT-RANGE-Y)
        new-touching (cond-> #{}
                       hit-left?   (conj :left)
                       hit-right?  (conj :right)
                       hit-top?    (conj :top)
                       hit-bottom? (conj :bottom))
        old-touching (or touching-walls #{})
        newly-hit-x? (and (or hit-left? hit-right?)
                          (not (or (:left old-touching) (:right old-touching))))
        newly-hit-y? (and (or hit-top? hit-bottom?)
                          (not (or (:top old-touching) (:bottom old-touching))))
        damage-delta (+ (if newly-hit-x? (wall-damage raw-v-x) 0.0)
                        (if newly-hit-y? (wall-damage raw-v-y) 0.0))]
    (merge robot {:pos-x (max 0.0 (min ROBOT-RANGE-X raw-pos-x))
                  :pos-y (max 0.0 (min ROBOT-RANGE-Y raw-pos-y))
                  :v-x   (if (or hit-left? hit-right?) 0.0 raw-v-x)
                  :v-y   (if (or hit-top? hit-bottom?) 0.0 raw-v-y)
                  :touching-walls new-touching
                  :damage (- damage damage-delta)})))

(defn- collision-damage
  "quadratic falloff in the approach speed along the contact normal"
  [approach-speed]
  (let [factor (/ approach-speed (* 2.0 V-MAX))]
    (* MAX-COLLISION-DAMAGE factor factor)))

(defn- resolve-collision-pair
  "given two robots that overlap and the previous-tick contact snapshot,
  return {:a robot-a' :b robot-b' :damage delta-or-zero} with:
  - positions separated to exactly 2 × ROBOT-RADIUS along the normal
  - normal-component velocities swapped iff the robots are approaching
  - damage delta applied iff this is a *transition* into contact"
  [a b was-touching?]
  (let [dx (- (:pos-x b) (:pos-x a))
        dy (- (:pos-y b) (:pos-y a))
        d2 (+ (* dx dx) (* dy dy))
        min-d (* 2.0 ROBOT-RADIUS)
        dist (physics/rw-sqrt (max d2 1e-12))
        nx (/ dx dist)
        ny (/ dy dist)
        approach (+ (* (- (:v-x a) (:v-x b)) nx)
                    (* (- (:v-y a) (:v-y b)) ny))
        approaching? (pos? approach)
        new-a-vx (if approaching? (- (:v-x a) (* approach nx)) (:v-x a))
        new-a-vy (if approaching? (- (:v-y a) (* approach ny)) (:v-y a))
        new-b-vx (if approaching? (+ (:v-x b) (* approach nx)) (:v-x b))
        new-b-vy (if approaching? (+ (:v-y b) (* approach ny)) (:v-y b))
        half-overlap (/ (- min-d dist) 2.0)
        clamp-x #(max 0.0 (min ROBOT-RANGE-X %))
        clamp-y #(max 0.0 (min ROBOT-RANGE-Y %))
        damage-delta (if (and (not was-touching?) approaching?)
                       (collision-damage approach)
                       0.0)]
    {:a (assoc a
               :pos-x (clamp-x (- (:pos-x a) (* half-overlap nx)))
               :pos-y (clamp-y (- (:pos-y a) (* half-overlap ny)))
               :v-x new-a-vx
               :v-y new-a-vy)
     :b (assoc b
               :pos-x (clamp-x (+ (:pos-x b) (* half-overlap nx)))
               :pos-y (clamp-y (+ (:pos-y b) (* half-overlap ny)))
               :v-x new-b-vx
               :v-y new-b-vy)
     :damage damage-delta}))

(defn collision-pass
  "single pass over pairs of alive robots. Detects circle-circle overlap,
  separates the robots along the contact normal, swaps normal-component
  velocities when approaching, and applies quadratic damage on the tick a
  pair transitions into contact. Refreshes :colliding-with sets to reflect
  the current tick's contacts."
  [robots]
  (let [alive-idxs (filterv #(:alive? (robots %)) (range (count robots)))
        old-touching (into {}
                           (for [idx alive-idxs]
                             [idx (or (:colliding-with (robots idx)) #{})]))
        pairs (for [i (range (count alive-idxs))
                    j (range (inc i) (count alive-idxs))]
                [(alive-idxs i) (alive-idxs j)])
        min-d (* 2.0 ROBOT-RADIUS)
        min-d2 (* min-d min-d)
        init-robots (mapv #(if (:alive? %) (assoc % :colliding-with #{}) %) robots)
        {:keys [robots damage]}
        (reduce
         (fn [{:keys [robots damage] :as acc} [a-idx b-idx]]
           (let [a (robots a-idx)
                 b (robots b-idx)
                 dx (- (:pos-x b) (:pos-x a))
                 dy (- (:pos-y b) (:pos-y a))
                 d2 (+ (* dx dx) (* dy dy))]
             (if (< d2 min-d2)
               (let [was-touching? (contains? (old-touching a-idx) b-idx)
                     {new-a :a new-b :b delta :damage} (resolve-collision-pair a b was-touching?)
                     new-a (update new-a :colliding-with conj b-idx)
                     new-b (update new-b :colliding-with conj a-idx)]
                 {:robots (assoc robots a-idx new-a b-idx new-b)
                  :damage (-> damage
                              (update a-idx (fnil + 0.0) delta)
                              (update b-idx (fnil + 0.0) delta))})
               acc)))
         {:robots init-robots :damage {}}
         pairs)]
    (mapv (fn [r]
            (if-let [d (get damage (:idx r))]
              (update r :damage - d)
              r))
          robots)))

(defn tick-robot
  "takes a robot and a world and returns the new state of the world
  after the robot has taken its turn."
  [{robot-idx :idx :as robot} world]
  (if (not (:alive? robot))
    world
    (let [ticked-world             (brain/tick-brain
                                    robot
                                    world
                                    register/read-register
                                    register/write-register)
          shot-timer-updated-world (update-robot
                                    robot-idx
                                    ticked-world
                                    update-shot-timer)
          moved-world              (update-robot
                                    robot-idx
                                    shot-timer-updated-world
                                    move-robot)]
      moved-world)))

