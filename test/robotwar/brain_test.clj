(ns robotwar.brain-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.brain])
  (:require [robotwar.world :as world]
            [robotwar.register :as register]
            [robotwar.test-programs :as test-programs]))

(def initial-world 
  (world/init-world 256 256 [test-programs/multi-use-program]))

(def worlds (iterate world/tick-world initial-world))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (get-in (world/get-world 4 0 worlds) 
                   [:robots 0 :brain :instr-ptr])
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (get-in (world/get-world 7 0 worlds) 
                   [:robots 0 :brain :acc])
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 5 0 worlds) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [9 [6]])))))

(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 9 0 worlds) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (world/get-world 8 0 worlds) 
                   [:robots 0 :brain :registers "A" :val])
           1))))
