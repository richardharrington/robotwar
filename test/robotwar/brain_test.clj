(ns robotwar.brain-test
  (:use [clojure.test]
        [robotwar.brain])
  (:require [robotwar.world :as world]))

(def src-codes [ ; program 0: multi-use program
                 " START 
                       0 TO A
                   TEST 
                       IF A > 2 GOTO START 
                       GOSUB INCREMENT
                       GOTO TEST 
                       100 TO A 
                   INCREMENT 
                       A + 1 TO A 
                       ENDSUB 
                       200 TO A "
                
                 ; program 1: to test RANDOM register
                 "     1000 TO RANDOM
                       RANDOM RANDOM RANDOM RANDOM RANDOM
                       RANDOM RANDOM RANDOM RANDOM RANDOM " 

                 ; program 2: to test INDEX/DATA pair of registers
                 "     300 TO A
                       1 TO INDEX
                       DATA " ])

(def initial-world (world/init-world 256 256 src-codes))
(def worlds (iterate world/tick-world initial-world))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (get-in (world/get-world 4 0 worlds) [:robots 0 :brain :instr-ptr])
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (get-in (world/get-world 7 0 worlds) [:robots 0 :brain :acc])
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 5 0 worlds) [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [9 [6]])))))

(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} 
              (get-in (world/get-world 9 0 worlds) [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (world/get-world 8 0 worlds) [:robots 0 :registers "A" :val])
           1))))

;(deftest random-test
;  (testing "push to random register and pull from it to receive a number
;           of unequal numbers less than the number that was pushed"
;    (is (let [random-pairs (map (fn [n]
;                                  (let [{{random "RANDOM"} :registers, acc :acc} 
;                                        (nth random-check-history n)]
;                                    [random acc])) 
;                                (range 3 13))]
;          (and (every? #{1000} (map first random-pairs))
;               (every? #(< -1 % 1000) (map second random-pairs))
;               (apply not= (map second random-pairs)))))))
;
;(deftest index-data-pair-test
;  (testing "registers whose index numbers are pushed to INDEX can
;           be referenced by accessing DATA"
;    (is (= (get-in (nth index-data-check-history 5) [:registers "A"])
;           300))))
