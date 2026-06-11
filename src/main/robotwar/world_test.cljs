(ns robotwar.world-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.world :refer [init-world tick-combined-world]]))

(deftest world-smoke-test
  (testing "init-world invariants and alive? skipping"
    (is (= 2 (count (:robots (init-world ["" ""])))))
    (is (every? :alive? (:robots (init-world ["" "" ""]))))
    (let [world (init-world ["" ""])
          dead-world (assoc-in world [:robots 0 :alive?] false)
          next-world (tick-combined-world dead-world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?]))))))
