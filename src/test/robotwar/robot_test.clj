(ns robotwar.robot-test
  (:require [clojure.test :refer :all]
            [robotwar.robot :refer :all]
            [robotwar.constants :refer :all]
            [robotwar.physics :as physics]
            [robotwar.register :as register]
            [robotwar.world :as world]))

(def world (world/init-world ["", ""]))

(defn- approx= [a b epsilon]
  (< (Math/abs (- a b)) epsilon))

(def x
  {:pos-key :pos-x
   :v-key :v-x
   :desired-v-key :desired-v-x
   :register-name-key "SPEEDX"})

(def y
  {:pos-key :pos-y
   :v-key :v-y
   :desired-v-key :desired-v-y
   :register-name-key "SPEEDY"})

(defn acceleration-test [pos speed {:keys [pos-key v-key desired-v-key register-name-key]} expected-sequence]
  (let [zeroed-world (assoc-in world [:robots 0 pos-key] pos)
        zeroed-registers (get-in world [:robots 0 :brain :registers])
        speedy-world (register/write-register (zeroed-registers register-name-key) zeroed-world speed)
        speedy-worlds (iterate (fn [{[robot] :robots :as world}]
                                 (binding [*GAME-SECONDS-PER-TICK* 1.0]
                                   (tick-robot robot world)))
                               speedy-world)]
    (is (= (take 6 (for [{[robot] :robots} speedy-worlds]
                     (select-keys robot [pos-key v-key desired-v-key])))
           expected-sequence))))

(deftest positive-acceleration-x-test
  (testing "application of SPEEDX register in positive direction has expected behavior"
    (acceleration-test
     0.0
     140
     x
     [{:pos-x 0.0, :v-x 0.0, :desired-v-x 14.0}
      {:pos-x 2.0, :v-x 4.0, :desired-v-x 14.0}
      {:pos-x 8.0, :v-x 8.0, :desired-v-x 14.0}
      {:pos-x 18.0, :v-x 12.0, :desired-v-x 14.0}
      {:pos-x 31.5, :v-x 14.0, :desired-v-x 14.0}
      {:pos-x 45.5, :v-x 14.0, :desired-v-x 14.0}])))

(deftest negative-acceleration-x-test
  (testing "application of SPEEDX register in negative direction has expected behavior"
    (acceleration-test
     100.0
     -140
     x
     [{:pos-x 100.0, :v-x 0.0, :desired-v-x -14.0}
      {:pos-x 98.0, :v-x -4.0, :desired-v-x -14.0}
      {:pos-x 92.0, :v-x -8.0, :desired-v-x -14.0}
      {:pos-x 82.0, :v-x -12.0, :desired-v-x -14.0}
      {:pos-x 68.5, :v-x -14.0, :desired-v-x -14.0}
      {:pos-x 54.5, :v-x -14.0, :desired-v-x -14.0}])))

(deftest positive-acceleration-y-test
  (testing "application of SPEEDY register in positive direction has expected behavior"
    (acceleration-test
     0.0
     140
     y
     [{:pos-y 0.0, :v-y 0.0, :desired-v-y 14.0}
      {:pos-y 2.0, :v-y 4.0, :desired-v-y 14.0}
      {:pos-y 8.0, :v-y 8.0, :desired-v-y 14.0}
      {:pos-y 18.0, :v-y 12.0, :desired-v-y 14.0}
      {:pos-y 31.5, :v-y 14.0, :desired-v-y 14.0}
      {:pos-y 45.5, :v-y 14.0, :desired-v-y 14.0}])))

(deftest negative-acceleration-y-test
  (testing "application of SPEEDY register in negative direction has expected behavior"
    (acceleration-test
     100.0
     -140
     y
     [{:pos-y 100.0, :v-y 0.0, :desired-v-y -14.0}
      {:pos-y 98.0, :v-y -4.0, :desired-v-y -14.0}
      {:pos-y 92.0, :v-y -8.0, :desired-v-y -14.0}
      {:pos-y 82.0, :v-y -12.0, :desired-v-y -14.0}
      {:pos-y 68.5, :v-y -14.0, :desired-v-y -14.0}
      {:pos-y 54.5, :v-y -14.0, :desired-v-y -14.0}])))

(defn- make-robot-at
  ([pos-x pos-y v-x v-y]
   (make-robot-at pos-x pos-y v-x v-y 0.0 0.0))
  ([pos-x pos-y v-x v-y desired-v-x desired-v-y]
   (make-robot-at 0 pos-x pos-y v-x v-y desired-v-x desired-v-y))
  ([idx pos-x pos-y v-x v-y desired-v-x desired-v-y]
   {:idx            idx
    :pos-x          pos-x
    :pos-y          pos-y
    :aim            0.0
    :damage         100.0
    :alive?         true
    :v-x            v-x
    :v-y            v-y
    :desired-v-x    desired-v-x
    :desired-v-y    desired-v-y
    :shot-timer     0.0
    :touching-walls #{}
    :colliding-with #{}}))

(deftest wall-damage-formula-test
  (testing "wall damage follows quadratic falloff in impact speed"
    ; max-speed impact: v-x and desired-v-x both at -V-MAX so no accel during the tick
    (let [robot (make-robot-at 1.0 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))]
      (is (approx= (- 100.0 MAX-WALL-DAMAGE) (:damage moved) 1e-10)
          "max-speed impact = MAX-WALL-DAMAGE")
      (is (contains? (:touching-walls moved) :left))
      (is (= 0.0 (:pos-x moved)))
      (is (= 0.0 (:v-x moved)) "perpendicular velocity zeroed"))

    ; half speed → quarter damage
    (let [half (/ V-MAX -2.0)
          robot (make-robot-at 1.0 100.0 half 0.0 half 0.0)
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))
          expected-dmg (* MAX-WALL-DAMAGE 0.25)]
      (is (approx= (- 100.0 expected-dmg) (:damage moved) 1e-10)))))

(deftest wall-damage-first-contact-only-test
  (testing "a robot pressed against a wall takes damage only on the first tick of contact"
    ; brain sets SPEEDX so desired-v-x pushes into the left wall each tick
    (let [robot0 (make-robot-at 0.5 100.0 -10.0 0.0 -20.0 0.0)
          tick1  (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot0))
          tick2  (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot (assoc tick1 :desired-v-x -20.0)))
          tick3  (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot (assoc tick2 :desired-v-x -20.0)))
          dmg-after-tick1 (- 100.0 (:damage tick1))]
      (is (pos? dmg-after-tick1) "took damage on first contact")
      (is (contains? (:touching-walls tick1) :left))
      (is (= (:damage tick1) (:damage tick2)) "no additional damage while still touching")
      (is (= (:damage tick2) (:damage tick3)) "no additional damage on further ticks")
      (is (= 0.0 (:v-x tick2)) "perpendicular velocity stays zeroed")
      (is (= 0.0 (:pos-x tick3)) "position clamped to wall"))))

(deftest wall-slide-preserves-parallel-motion-test
  (testing "moving diagonally into a wall preserves the parallel velocity component"
    ; robot near left wall moving in +y and -x: -x component gets zeroed at wall,
    ; +y component continues unchanged
    (let [robot (make-robot-at 0.5 100.0 -10.0 5.0 -10.0 5.0)
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))]
      (is (= 0.0 (:pos-x moved)) "clamped to left wall")
      (is (= 0.0 (:v-x moved))   "perpendicular velocity zeroed")
      (is (contains? (:touching-walls moved) :left))
      (is (not (contains? (:touching-walls moved) :top)))
      (is (not (contains? (:touching-walls moved) :bottom)))
      (is (pos? (:v-y moved))    "parallel velocity preserved")
      (is (> (:pos-y moved) 100.0) "parallel motion happened"))))

(deftest wall-touching-walls-transitions-test
  (testing "leaving a wall clears its :touching-walls flag so re-impact re-damages"
    ; Tick 1: hit left wall going left, take damage.
    (let [robot0 (make-robot-at 0.5 100.0 -25.5 0.0)
          tick1  (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot0))]
      (is (contains? (:touching-walls tick1) :left))
      ; Tick 2: brain sets +x velocity, robot moves away from wall.
      (let [away  (assoc tick1 :v-x 5.0 :desired-v-x 5.0)
            tick2 (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot away))]
        (is (not (contains? (:touching-walls tick2) :left)))
        (is (pos? (:pos-x tick2)))
        ; Tick 3: brain reverses direction, robot slams back into wall.
        (let [back  (assoc tick2 :v-x -25.5 :desired-v-x -25.5 :pos-x 0.5)
              tick3 (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot back))]
          (is (contains? (:touching-walls tick3) :left))
          (is (< (:damage tick3) (:damage tick2))
              "re-hitting the wall applies damage again"))))))

(deftest wall-position-clamped-to-arena-test
  (testing "position never exceeds the arena bounds regardless of impact"
    (let [right (binding [*GAME-SECONDS-PER-TICK* 1.0]
                  (move-robot (make-robot-at (- ROBOT-RANGE-X 0.5) 100.0 V-MAX 0.0 V-MAX 0.0)))
          top   (binding [*GAME-SECONDS-PER-TICK* 1.0]
                  (move-robot (make-robot-at 100.0 0.5 0.0 (- V-MAX) 0.0 (- V-MAX))))
          bot   (binding [*GAME-SECONDS-PER-TICK* 1.0]
                  (move-robot (make-robot-at 100.0 (- ROBOT-RANGE-Y 0.5) 0.0 V-MAX 0.0 V-MAX)))]
      (is (= ROBOT-RANGE-X (:pos-x right)))
      (is (contains? (:touching-walls right) :right))
      (is (= 0.0 (:pos-y top)))
      (is (contains? (:touching-walls top) :top))
      (is (= ROBOT-RANGE-Y (:pos-y bot)))
      (is (contains? (:touching-walls bot) :bottom)))))

(deftest wall-corner-damages-both-axes-test
  (testing "hitting a corner applies damage from both axes on the same tick"
    (let [robot (make-robot-at 0.5 0.5 (- V-MAX) (- V-MAX) (- V-MAX) (- V-MAX))
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))
          expected-dmg (* 2.0 MAX-WALL-DAMAGE)]
      (is (contains? (:touching-walls moved) :left))
      (is (contains? (:touching-walls moved) :top))
      (is (= 0.0 (:pos-x moved)))
      (is (= 0.0 (:pos-y moved)))
      (is (approx= (- 100.0 expected-dmg) (:damage moved) 1e-10)))))

(deftest collision-head-on-max-damage-test
  (testing "head-on collision at max speed applies MAX-COLLISION-DAMAGE to each robot"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          result (collision-pass [a b])]
      (is (approx= (- 100.0 MAX-COLLISION-DAMAGE) (:damage (result 0)) 1e-10))
      (is (approx= (- 100.0 MAX-COLLISION-DAMAGE) (:damage (result 1)) 1e-10))
      (is (contains? (:colliding-with (result 0)) 1))
      (is (contains? (:colliding-with (result 1)) 0)))))

(deftest collision-glancing-small-damage-test
  (testing "glancing collision applies small damage proportional to approach-speed squared"
    ; robots approaching at V-MAX/4 along x, offset in y — approach normal ≈ +x direction
    (let [gap (* 0.5 ROBOT-RADIUS)
          v (/ V-MAX 4.0)
          a (make-robot-at 0 (- 100.0 gap) 100.0 v 0.0 v 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- v) 0.0 (- v) 0.0)
          result (collision-pass [a b])
          expected-dmg (* MAX-COLLISION-DAMAGE (/ 1.0 16.0))]
      (is (approx= (- 100.0 expected-dmg) (:damage (result 0)) 1e-10))
      (is (approx= (- 100.0 expected-dmg) (:damage (result 1)) 1e-10))
      (is (< expected-dmg (/ MAX-COLLISION-DAMAGE 4.0))))))

(deftest collision-stationary-no-damage-test
  (testing "two stationary robots in contact take no damage"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 0.0 0.0 0.0 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 0.0 0.0 0.0 0.0)
          result (collision-pass [a b])]
      (is (= 100.0 (:damage (result 0))))
      (is (= 100.0 (:damage (result 1))))
      (is (contains? (:colliding-with (result 0)) 1))
      (is (contains? (:colliding-with (result 1)) 0)))))

(deftest collision-first-contact-only-test
  (testing "damage is applied only on the tick a pair transitions into contact"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          tick1 (collision-pass [a b])
          ; simulate them still touching but no longer approaching (velocities swapped last tick)
          tick2 (collision-pass tick1)]
      ; both robots damaged on tick1
      (is (< (:damage (tick1 0)) 100.0))
      ; but tick2 does NOT damage them again even if still in contact
      (is (= (:damage (tick1 0)) (:damage (tick2 0))))
      (is (= (:damage (tick1 1)) (:damage (tick2 1)))))))

(deftest collision-circle-not-square-test
  (testing "robots at diagonal offset just outside circle-circle radius do NOT collide"
    ; distance = 1.5 × (2R), placed on the diagonal — old square logic would have collided
    (let [d (* 1.5 ROBOT-RADIUS)  ; per-axis offset; total distance = d * √2 ≈ 2.12 × ROBOT-RADIUS > 2R
          a (make-robot-at 0 100.0 100.0 0.0 0.0 0.0 0.0)
          b (make-robot-at 1 (+ 100.0 d) (+ 100.0 d) 0.0 0.0 0.0 0.0)
          result (collision-pass [a b])]
      (is (empty? (:colliding-with (result 0))))
      (is (empty? (:colliding-with (result 1))))
      (is (= 100.0 (:damage (result 0)))))))

(deftest collision-separation-test
  (testing "after a collision the two robots are exactly 2 × ROBOT-RADIUS apart along the normal"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          result (collision-pass [a b])
          dx (- (:pos-x (result 1)) (:pos-x (result 0)))
          dy (- (:pos-y (result 1)) (:pos-y (result 0)))
          dist (physics/rw-sqrt (+ (* dx dx) (* dy dy)))]
      (is (approx= (* 2.0 ROBOT-RADIUS) dist 1e-10)))))

(deftest collision-velocity-swap-test
  (testing "head-on collision along +x swaps normal-component velocities"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          result (collision-pass [a b])]
      (is (approx= (- V-MAX) (:v-x (result 0)) 1e-10))
      (is (approx= V-MAX (:v-x (result 1)) 1e-10)))))

(deftest collision-not-approaching-no-damage-test
  (testing "overlapping robots moving apart take no damage but still get separated"
    ; already overlapping and moving in the same direction — B moving away from A faster
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (make-robot-at 0 (- 100.0 gap) 100.0 5.0 0.0 5.0 0.0)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 10.0 0.0 10.0 0.0)
          result (collision-pass [a b])
          dx (- (:pos-x (result 1)) (:pos-x (result 0)))]
      (is (= 100.0 (:damage (result 0))))
      (is (= 100.0 (:damage (result 1))))
      (is (approx= (* 2.0 ROBOT-RADIUS) dx 1e-10)))))

(deftest collision-dead-robot-excluded-test
  (testing "a dead robot is not considered as a collision target"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (assoc (make-robot-at 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
                   :alive? false)
          b (make-robot-at 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          result (collision-pass [a b])]
      (is (= 100.0 (:damage (result 1))) "living robot untouched")
      (is (empty? (:colliding-with (result 1)))))))
