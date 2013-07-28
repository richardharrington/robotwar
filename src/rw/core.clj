(ns rw.core
  (:require (rw [create :as create]
                [exec :as exec])))

(def robot-source-code-test "WAIT GOTO WAIT")
(def robot-program-test (create/compile robot-source-code-test))

(def robot-state-test {:acc nil
                       :instr-ptr 0
                       :call-stack []
                       :registers nil
                       :program robot-program-test})

(def rc "AIM + 5 TO RADAR")
(def rp (create/compile rc))
(def rs (assoc-in (exec/init-robot rp) [:registers "AIM"] 6))

(def t0 (exec/tick-robot rs))
(def t1 (exec/tick-robot t0))
(def t3 (exec/tick-robot t1))
