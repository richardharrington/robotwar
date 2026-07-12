(ns robotwar.world-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.world :refer [init-world tick-combined-world]]
            [robotwar.robot :as robot]))

(deftest world-smoke-test
  (testing "init-world invariants and alive? skipping"
    (is (= 2 (count (:robots (init-world ["" ""])))))
    (is (every? :alive? (:robots (init-world ["" "" ""]))))
    (let [world (init-world ["" ""])
          dead-world (assoc-in world [:robots 0 :alive?] false)
          next-world (tick-combined-world dead-world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?]))))))

(deftest world-shell-damage-smoke-test
  (testing "shell explosion damages nearby robots in CLJS"
    (let [robot (robot/init-robot 0 "" {:pos-x 100.0 :pos-y 100.0 :aim 0.0 :damage 100.0})
          shell {:id 0 :pos-x 100.0 :pos-y 100.0 :v-x 0.0 :v-y 0.0 :dest-x 100.0 :dest-y 100.0 :exploded false}
          world {:shells {0 shell} :robots [robot] :next-shell-id 1}
          next-world (tick-combined-world world)]
      (is (= 70.0 (get-in next-world [:robots 0 :damage]))))))

(deftest world-death-victory-smoke-test
  (testing "death and victory detection in CLJS"
    (let [robot0 (robot/init-robot 0 "" {:pos-x 100.0 :pos-y 100.0 :aim 0.0 :damage 0.0})
          robot1 (robot/init-robot 1 "" {:pos-x 200.0 :pos-y 200.0 :aim 0.0 :damage 100.0})
          world {:shells {} :robots [robot0 robot1] :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?])))
      (is (= {:winner 1} (:result next-world))))))

(deftest world-died-at-tick-smoke-test
  (testing "tick counter advances and deaths record died-at-tick in CLJS"
    (let [world (assoc-in (init-world ["" ""]) [:robots 0 :damage] 0.0)
          next-world (tick-combined-world world)]
      (is (= 0 (:tick world)))
      (is (= 1 (:tick next-world)))
      (is (= 1 (get-in next-world [:robots 0 :died-at-tick])))
      (is (nil? (get-in next-world [:robots 1 :died-at-tick]))))))

(deftest world-tie-smoke-test
  (testing "tie detection with just-died indices in CLJS"
    (let [robot0 (robot/init-robot 0 "" {:pos-x 100.0 :pos-y 100.0 :aim 0.0 :damage 0.0})
          robot1 (robot/init-robot 1 "" {:pos-x 200.0 :pos-y 200.0 :aim 0.0 :damage 0.0})
          world {:shells {} :robots [robot0 robot1] :next-shell-id 0}
          next-world (tick-combined-world world)
          result (:result next-world)]
      (is (nil? (:winner result)))
      (is (= {:game-over? true} result))
      (is (= #{0 1} (set (:just-died next-world)))))))
