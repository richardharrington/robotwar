(ns robotwar.test-runner
  (:require [cljs.test :as t]
            [robotwar.assembler-test]
            [robotwar.brain-test]
            [robotwar.register-test]
            [robotwar.robot-test]))

(defn -main []
  (t/run-all-tests #"^robotwar\..*-test$"))

(set! *main-cli-fn* -main)
