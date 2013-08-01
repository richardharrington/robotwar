(ns robotwar.robot-test
  (:use [clojure.test]
        (robotwar foundry robot game-lexicon)))

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
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM
                       RANDOM " 

                 ; program 2: to test INDEX/DATA pair of registers
                 "     300 TO A
                       1 TO INDEX
                       DATA " ])

(def robot-history #(iterate tick-robot (init-robot-state (assemble reg-names %) {})))
(def robot-histories (map robot-history src-codes))
(def multi-use-history (nth robot-histories 0))
(def random-check-history (nth robot-histories 1))
(def index-data-check-history (nth robot-histories 2)) 

(deftest branching-test
  (testing "comparison statement should cause jump in instr-ptr"
    (is (= (:instr-ptr (nth multi-use-history 4))
           5))))

(deftest arithmetic-test
  (testing "addition"
    (is (= (:acc (nth multi-use-history 7))
           1))))

(deftest gosub-test
  (testing "gosub should move instr-ptr and add the return-ptr to the call stack"
    (is (let [{:keys [instr-ptr call-stack]} (nth multi-use-history 5)]
          (= [instr-ptr call-stack]
             [9 [6]])))))
          
(deftest endsub-test
  (testing "endsub pops instr-ptr off call stack and goes there"
    (is (let [{:keys [instr-ptr call-stack]} (nth multi-use-history 9)]
          (= [instr-ptr call-stack]
             [6 []])))))

(deftest push-test
  (testing "pushing number to register"
    (is (= (get-in (nth multi-use-history 8) [:registers "A"])
           1))))

(deftest random-test
  (testing "push to random register and pull from it to receive a number
           of unequal numbers less than the number that was pushed"
    (is (let [random-pairs (map (fn [n]
                                  (let [{{random "RANDOM"} :registers, acc :acc} 
                                        (nth random-check-history n)]
                                    [random acc])) 
                                (range 3 13))]
          (and (every? #{1000} (map first random-pairs))
               (every? #(< -1 % 1000) (map second random-pairs))
               (apply not= (map second random-pairs)))))))

(deftest index-data-pair-test
  (testing "registers whose index numbers are pushed to INDEX can
           be referenced by accessing DATA"
    (is (= (get-in (nth index-data-check-history 5) [:registers "A"])
           300))))

