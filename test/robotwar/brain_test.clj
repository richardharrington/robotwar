(ns robotwar.brain-test
  (:use [clojure.test]
        [robotwar.brain])
  (:require (robotwar foundry game-lexicon)))

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

(def len (count src-codes))
(def idx-range (range len))

(def robot-registers-vecs 
  (for [idx idx-range]
    (for [reg-name robotwar.game-lexicon/reg-names]
      (into {} (let [path-to-val [:robots idx :registers reg-name :val]]
                 {reg-name {:read (fn [world]
                                    (get-in world path-to-val))
                            :write (fn [world data]
                                     (assoc-in world path-to-val data))
                            :val 0}})))))

(def brains (map (comp init-brain (partial robotwar.foundry/assemble robotwar.game-lexicon/reg-names))
                 src-codes))

(def robots (vec (map (fn [brain robot-registers]
                        {:brain brain :registers robot-registers})
                      brains
                      robot-registers-vecs)))

(def initial-world {:robots robots})

; this next test structure is super-cheap and hacky (don't know why it won't work
; with infinite sequences) but I just have to get it over with.
(def worlds (vec (take 100 (map first (iterate (fn [[world idx]]
                                                 (step-brain world (mod (inc idx) len)))
                                               [initial-world -1])))))

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (get-in (nth worlds 4) [:robots 0 :brain :instr-ptr])
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (get-in (nth worlds 7) [:robots 0 :brain :acc])
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} (get-in (nth worlds 7) [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [9 [6]])))))

(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} (get-in (nth worlds 9) [:robots 0 :brain])]
          (= [instr-ptr call-stack]
             [6 []])))))

;(deftest push-test
;  (testing "pushing number to register"
;    (is (= (get-in (nth worlds 7) [:robots 0 :brain :acc])
;    (is (= (get-in (nth multi-use-history 8) [:registers "A"])
;           1))))
;
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
