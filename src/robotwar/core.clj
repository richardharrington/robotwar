(ns robotwar.core
  (:refer-clojure :exclude [compile])
  (:use [clojure.pprint]
        (robotwar create exec)))

(def robot-source-code-test "WAIT GOTO WAIT")
(def robot-program-test (compile robot-source-code-test))

(def robot-state-test {:acc nil
                       :instr-ptr 0
                       :call-stack []
                       :registers nil
                       :program robot-program-test})

(def rc "AIM + 5 TO RADAR")
(def rp (compile rc))
(def t0 (assoc-in (init-robot rp) [:registers "AIM"] 6))

(def t1 (tick-robot t0))
(def t2 (tick-robot t1))
(def t3 (tick-robot t2))

(def iter (iterate tick-robot t0))

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

(def istate (init-robot (compile src-code)))

(def s (iterate tick-robot istate))
(def t #(nth s %))

; pretty-prints a robot-state with line numbers, 
; and only the first four registers. very convenient.
(def ppt #(pprint (into (assoc-in (t %) 
                                  [:program :instrs] 
                                  (zipmap (range) (get-in (t %) [:program :instrs])))
                        {:registers (select-keys ((t %) :registers) ["A" "B" "C" "D"])})))

