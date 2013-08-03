(ns robotwar.core
  (:use [clojure.pprint]
        (robotwar foundry brain robot world game-lexicon brain-test)))

; this is a hacky place for messing with stuff. currently imports 
; all the test data from brain-test, and the function below uses
; some of those variables to 
; pretty-print a robot-state with line numbers for the program instructions, 
; and only the registers you want. Very convenient.
;
; it takes a world-tick number and a robot index number, and prettyprints a robot
; with line numbers for the program instructions, and only the registers specified.
; (also it only prints the values of the registers, not the register-maps with
; their ugly full system-names of the read and write functions.) Very convenient.

(def get-robot (fn [world-tick-idx robot-idx]
                 ((:robots (get-world world-tick-idx robot-idx)) robot-idx)))

(def ppt (fn [world-tick-idx robot-idx & [reg-keys]]
           (let [{:keys [brain registers] :as robot} (get-robot world-tick-idx robot-idx)]
             (pprint 
               (into robot 
                     {:brain (assoc-in 
                               brain 
                               [:program :instrs]
                               (sort (zipmap (range) (get-in brain [:program :instrs]))))
                      :registers (sort (into {} (for [[reg-name reg-map]
                                                      (select-keys registers reg-keys)]
                                                  {reg-name (:val reg-map)})))})))))
