(ns robotwar.brain-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.world :as world]))

(def multi-use-program
  "\n  START\n      0 TO A\n  TEST\n      IF A > 2 GOTO START\n      GOSUB INCREMENT\n      GOTO TEST\n      100 TO A\n  INCREMENT\n      A + 1 TO A\n      ENDSUB\n      200 TO A\n")

(def initial-world (world/init-world [multi-use-program]))
(def combined-worlds (world/build-combined-worlds initial-world))

(deftest brain-flow-test
  (testing "branching/call stack/register updates"
    (is (= 5 (get-in (nth combined-worlds 4) [:robots 0 :brain :instr-ptr])))
    (is (= 1 (get-in (nth combined-worlds 7) [:robots 0 :brain :acc])))
    (is (= [9 [6]] (let [{:keys [instr-ptr call-stack]} (get-in (nth combined-worlds 5) [:robots 0 :brain])]
                     [instr-ptr call-stack])))
    (is (= [6 []] (let [{:keys [instr-ptr call-stack]} (get-in (nth combined-worlds 9) [:robots 0 :brain])]
                    [instr-ptr call-stack])))
    (is (= 1 (get-in (nth combined-worlds 8) [:robots 0 :brain :registers "A" :val])))))
