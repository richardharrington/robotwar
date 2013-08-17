(ns robotwar.brain-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.brain])
  (:require [robotwar.world :as world]
            [robotwar.register :as register]
            [robotwar.test-programs :as test-programs]))

(def initial-world 
  (world/init-world 256.0 256.0 [(:multi-use test-programs/programs)]))

(def combined-worlds (world/build-combined-worlds initial-world))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (get-in (nth combined-worlds 4) 
                   [:robots 0 :brain :instr-ptr])
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (get-in (nth combined-worlds 7) 
                   [:robots 0 :brain :acc])
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (nth combined-worlds 5) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [9 [6]])))))

(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (nth combined-worlds 9) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (nth combined-worlds 8) 
                   [:robots 0 :brain :registers "A" :val])
           1))))
