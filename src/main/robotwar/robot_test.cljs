(ns robotwar.robot-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*
                                        MAX-WALL-DAMAGE MAX-COLLISION-DAMAGE
                                        ROBOT-RADIUS V-MAX]]
            [robotwar.register :as register]
            [robotwar.robot :refer [tick-robot move-robot collision-pass]]
            [robotwar.world :as world]))

(def world (world/init-world ["" ""]))

(defn acceleration-seq [pos speed pos-key v-key desired-v-key reg]
  (let [w0 (assoc-in world [:robots 0 pos-key] pos)
        regs (get-in world [:robots 0 :brain :registers])
        w1 (register/write-register (regs reg) w0 speed)
        ws (iterate (fn [{[robot] :robots :as w}]
                      (binding [*GAME-SECONDS-PER-TICK* 1.0]
                        (tick-robot robot w)))
                    w1)]
    (take 3 (for [{[robot] :robots} ws]
              (select-keys robot [pos-key v-key desired-v-key])))))

(deftest robot-acceleration-smoke-test
  (testing "acceleration starts with expected sequence"
    (is (= [{:pos-x 0.0 :v-x 0.0 :desired-v-x 14.0}
            {:pos-x 2.0 :v-x 4.0 :desired-v-x 14.0}
            {:pos-x 8.0 :v-x 8.0 :desired-v-x 14.0}]
           (acceleration-seq 0.0 140 :pos-x :v-x :desired-v-x "SPEEDX")))))

(defn- test-robot
  ([pos-x pos-y v-x v-y desired-v-x desired-v-y]
   (test-robot 0 pos-x pos-y v-x v-y desired-v-x desired-v-y))
  ([idx pos-x pos-y v-x v-y desired-v-x desired-v-y]
   {:idx idx :pos-x pos-x :pos-y pos-y :aim 0.0 :damage 100.0
    :alive? true :v-x v-x :v-y v-y :desired-v-x desired-v-x :desired-v-y desired-v-y
    :shot-timer 0.0 :touching-walls #{} :colliding-with #{}}))

(defn- approx= [a b eps]
  (< (js/Math.abs (- a b)) eps))

(deftest wall-smoke-test
  (testing "wall clamps position, zeroes perpendicular velocity, and damages on first contact"
    (let [robot (test-robot 1.0 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))]
      (is (= 0.0 (:pos-x moved)))
      (is (= 0.0 (:v-x moved)))
      (is (contains? (:touching-walls moved) :left))
      (is (approx= (- 100.0 MAX-WALL-DAMAGE) (:damage moved) 1e-10))))
  (testing "diagonal impact zeroes perpendicular but preserves parallel motion"
    (let [robot (test-robot 0.5 100.0 -10.0 5.0 -10.0 5.0)
          moved (binding [*GAME-SECONDS-PER-TICK* 1.0] (move-robot robot))]
      (is (= 0.0 (:pos-x moved)))
      (is (= 0.0 (:v-x moved)))
      (is (pos? (:v-y moved)))
      (is (> (:pos-y moved) 100.0))))
  (testing "no repeat damage while continuously pressed against a wall"
    (let [tick1 (binding [*GAME-SECONDS-PER-TICK* 1.0]
                  (move-robot (test-robot 0.5 100.0 -10.0 0.0 -20.0 0.0)))
          tick2 (binding [*GAME-SECONDS-PER-TICK* 1.0]
                  (move-robot (assoc tick1 :desired-v-x -20.0)))]
      (is (contains? (:touching-walls tick1) :left))
      (is (= (:damage tick1) (:damage tick2))))))

(deftest collision-smoke-test
  (testing "head-on max-speed collision applies MAX-COLLISION-DAMAGE and swaps normal velocities"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (test-robot 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (test-robot 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          result (collision-pass [a b])]
      (is (approx= (- 100.0 MAX-COLLISION-DAMAGE) (:damage (result 0)) 1e-10))
      (is (approx= (- 100.0 MAX-COLLISION-DAMAGE) (:damage (result 1)) 1e-10))
      (is (approx= (- V-MAX) (:v-x (result 0)) 1e-10))
      (is (approx= V-MAX (:v-x (result 1)) 1e-10))
      (is (contains? (:colliding-with (result 0)) 1))))
  (testing "diagonal-offset just outside 2R does not collide (circle-circle, not square)"
    (let [d (* 1.5 ROBOT-RADIUS)  ; per-axis; total distance ≈ 2.12 × ROBOT-RADIUS > 2R
          a (test-robot 0 100.0 100.0 0.0 0.0 0.0 0.0)
          b (test-robot 1 (+ 100.0 d) (+ 100.0 d) 0.0 0.0 0.0 0.0)
          result (collision-pass [a b])]
      (is (empty? (:colliding-with (result 0))))
      (is (= 100.0 (:damage (result 0))))))
  (testing "glancing contact at 45° deals angle-scaled damage (normal component only)"
    (let [h (/ ROBOT-RADIUS (js/Math.sqrt 2.0))
          a (test-robot 0 100.0 100.0 V-MAX 0.0 V-MAX 0.0)
          b (test-robot 1 (+ 100.0 h) (+ 100.0 h) 0.0 0.0 0.0 0.0)
          result (collision-pass [a b])]
      (is (approx= (- 100.0 (/ MAX-COLLISION-DAMAGE 8.0)) (:damage (result 0)) 1e-10))
      (is (approx= (- 100.0 (/ MAX-COLLISION-DAMAGE 8.0)) (:damage (result 1)) 1e-10))))
  (testing "stationary contact takes no damage"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (test-robot 0 (- 100.0 gap) 100.0 0.0 0.0 0.0 0.0)
          b (test-robot 1 (+ 100.0 gap) 100.0 0.0 0.0 0.0 0.0)
          result (collision-pass [a b])]
      (is (= 100.0 (:damage (result 0))))
      (is (= 100.0 (:damage (result 1))))))
  (testing "damage applies only on transition into contact"
    (let [gap (* 0.5 ROBOT-RADIUS)
          a (test-robot 0 (- 100.0 gap) 100.0 V-MAX 0.0 V-MAX 0.0)
          b (test-robot 1 (+ 100.0 gap) 100.0 (- V-MAX) 0.0 (- V-MAX) 0.0)
          tick1 (collision-pass [a b])
          tick2 (collision-pass tick1)]
      (is (< (:damage (tick1 0)) 100.0))
      (is (= (:damage (tick1 0)) (:damage (tick2 0))))
      (is (= (:damage (tick1 1)) (:damage (tick2 1)))))))
