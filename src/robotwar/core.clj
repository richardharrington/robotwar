(ns robotwar.core
  (:refer-clojure :exclude [compile])
  (:use [clojure.pprint]
        (robotwar create exec)))

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

(def world (init-world 30 30 (map compile [src-code1 src-code2 src-code3])))





; pretty-prints a robot-state with line numbers, 
; and only the first four registers. very convenient.
#_(def ppt #(pprint (into (assoc-in (t %) 
                                  [:program :instrs] 
                                  (zipmap (range) (get-in (t %) [:program :instrs])))
                        {:registers (select-keys ((t %) :registers) ["A" "B" "C" "D"])})))

