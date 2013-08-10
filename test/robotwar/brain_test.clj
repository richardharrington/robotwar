(ns robotwar.brain-test
  (:use [clojure.test]
        [robotwar.brain])
  (:require [robotwar.world :as world]
            [robotwar.register :as register]
            [robotwar.test-programs :as test-programs]))

(def initial-multi-use-world 
  (world/init-world 256 256 [test-programs/multi-use-program]))
(def initial-index-data-world 
  (world/init-world 256 256 [test-programs/index-data-program]))

(def multi-use-worlds (iterate world/tick-world initial-multi-use-world))
(def index-data-worlds (iterate world/tick-world initial-index-data-world))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (get-in (world/get-world 4 0 multi-use-worlds) 
                   [:robots 0 :brain :instr-ptr])
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (get-in (world/get-world 7 0 multi-use-worlds) 
                   [:robots 0 :brain :acc])
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 5 0 multi-use-worlds) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [9 [6]])))))

(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 9 0 multi-use-worlds) 
                      [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (world/get-world 8 0 multi-use-worlds) 
                   [:robots 0 :brain :registers "A" :val])
           1))))

(deftest index-data-pair-test
  (testing "registers whose index numbers are push to INDEX can
           be referenced by accessing DATA"
    (is (= (get-in (world/get-world 5 0 index-data-worlds) 
                   [:robots 0 :brain :registers "A" :val])
           300))))

; last test will use a different method:
; just push and pull from one sample world and one sample robot

(def initial-multi-use-robot ((:robots initial-multi-use-world) 0))

(deftest random-test
  (testing "push to random register and pull a series of numbers all different
           from random register"
    (let [random-register (get-in initial-multi-use-robot [:brain :registers "RANDOM"])
          new-world (register/write-register random-register initial-multi-use-world 1000)
          random-nums (repeatedly 5 (partial register/read-register random-register new-world))]
    (is (= (get-in new-world [:robots 0 :brain :registers "RANDOM" :val])
           1000))
    (is (every? #(< -1 % 1000) random-nums))))) 

