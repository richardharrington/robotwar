(ns robotwar.exec-test
  (:refer-clojure :exclude [compile])
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

(def initial-state (init-robot (compile src-code)))
(def states (iterate tick-robot initial-state))
(def nth-state #(nth states %))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (:instr-ptr (nth-state 4))
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (:acc (nth-state 7))
           1))))

(deftest gosub-and-call-stack-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} (nth-state 5)]
          (= [instr-ptr call-stack]
             [9 [6]])))))
          
(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} (nth-state 9)]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (nth-state 8) [:registers "A"])
           1))))
