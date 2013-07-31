(ns robotwar.exec-test
  (:require [clojure.test :refer :all]
            (robotwar [create :refer :all]
                      [exec :refer :all])))

(def src-code " START 
                    0 TO A
                TEST 
                    IF A > 2 GOTO START 
                    GOSUB INCREMENT
                    GOTO TEST 
                    100 TO A 
                INCREMENT 
                    A + 1 TO A 
                    ENDSUB 
                    200 TO A ")

(def initial-state (init-robot-state (assemble src-code) {}))
(def states (iterate tick-robot initial-state))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (:instr-ptr (nth states 4))
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (:acc (nth states 7))
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} (nth states 5)]
          (= [instr-ptr call-stack]
             [9 [6]])))))
          
(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} (nth states 9)]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (nth states 8) [:registers "A"])
           1))))
