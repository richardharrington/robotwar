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

