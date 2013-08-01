(ns robotwar.core
  (:use [clojure.pprint]
        (robotwar foundry robot world game-lexicon)))

(def src-code1 " START 
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

(def src-code2 "WAIT GOTO WAIT")
(def src-code3 "500 TO RANDOM RANDOM RANDOM RANDOM")

(def world (init-world 30 30 (map #(assemble reg-names %) [src-code1 src-code2 src-code3])))

(def step (fn [initial-state n]
            (nth (iterate tick-robot initial-state) n)))

; pretty-prints a robot-state with line numbers, 
; and only the registers you want. Very convenient.

(def ppt (fn [program n & reg-keys]
           (let [state (step (init-robot-state program {}) n)]
             (pprint (into (assoc-in
                             state
                             [:program :instrs]
                             (zipmap (range) (get-in state [:program :instrs])))
                           {:registers (select-keys (:registers state) reg-keys)}))))) 

