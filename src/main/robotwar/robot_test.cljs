(ns robotwar.robot-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*
                                        MAX-WALL-DAMAGE V-MAX ROBOT-RANGE-X]]
            [robotwar.register :as register]
            [robotwar.robot :refer [tick-robot move-robot]]
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

(defn- test-robot [pos-x pos-y v-x v-y desired-v-x desired-v-y]
  {:idx 0 :pos-x pos-x :pos-y pos-y :aim 0.0 :damage 100.0
   :alive? true :v-x v-x :v-y v-y :desired-v-x desired-v-x :desired-v-y desired-v-y
   :shot-timer 0.0 :touching-walls #{}})

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
