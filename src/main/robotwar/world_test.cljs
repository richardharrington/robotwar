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
          world {:shells [shell] :robots [robot] :next-shell-id 1}
          next-world (tick-combined-world world)]
      (is (= 70.0 (get-in next-world [:robots 0 :damage]))))))
