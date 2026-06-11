(ns robotwar.world-test
  (:require [clojure.test :refer :all]
            [robotwar.world :refer :all]))

(deftest init-world-valid-counts-test
  (testing "init-world succeeds with 2 to 5 programs"
    (is (= 2 (count (:robots (init-world ["" ""])))))
    (is (= 3 (count (:robots (init-world ["" "" ""])))))
    (is (= 5 (count (:robots (init-world ["" "" "" "" ""])))))))

(deftest init-world-alive-test
  (testing "all robots start alive"
    (let [world (init-world ["" ""])]
      (is (every? :alive? (:robots world))))))

(deftest init-world-too-few-test
  (testing "init-world throws with fewer than 2 programs"
    (is (thrown? clojure.lang.ExceptionInfo
                 (init-world [""])))
    (let [ex (try (init-world [""])
                  (catch clojure.lang.ExceptionInfo e e))]
      (when ex
        (is (= :insufficient-programs (-> ex ex-data :error)))))))

(deftest init-world-too-many-test
  (testing "init-world throws with more than 5 programs"
    (is (thrown? clojure.lang.ExceptionInfo
                 (init-world ["" "" "" "" "" ""])))
    (let [ex (try (init-world ["" "" "" "" "" ""])
                  (catch clojure.lang.ExceptionInfo e e))]
      (when ex
        (is (= :too-many-programs (-> ex ex-data :error)))))))

(deftest tick-combined-world-skips-dead-robots-test
  (testing "tick-combined-world does not tick dead robots"
    (let [world (init-world ["" "90 TO AIM"])
          dead-world (assoc-in world [:robots 0 :alive?] false)
          next-world (tick-combined-world dead-world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?])))
      (is (= 0 (get-in next-world [:robots 0 :brain :instr-ptr])))
      (is (= 1 (get-in next-world [:robots 1 :brain :instr-ptr]))))))
