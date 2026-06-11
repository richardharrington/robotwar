(ns robotwar.robot-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.constants :refer [*GAME-SECONDS-PER-TICK*]]
            [robotwar.register :as register]
            [robotwar.robot :refer [tick-robot]]
            [robotwar.world :as world]))

(def world (world/init-world ["" ""]))

(defn acceleration-seq [pos speed pos-key v-key desired-v-key reg]
  (let [w0 (assoc-in world [:robots 0 pos-key] pos)
        regs (get-in world [:robots 0 :brain :registers])
        w1 (register/write-register (regs reg) w0 speed)
        ws (iterate (fn [{[robot] :robots :as w}]
                      (binding [*GAME-SECONDS-PER-TICK* 1.0]
                        (tick-robot robot w)))
                    w1)]
    (take 3 (for [{[robot] :robots} ws]
              (select-keys robot [pos-key v-key desired-v-key])))))

(deftest robot-acceleration-smoke-test
  (testing "acceleration starts with expected sequence"
    (is (= [{:pos-x 0.0 :v-x 0.0 :desired-v-x 14.0}
            {:pos-x 2.0 :v-x 4.0 :desired-v-x 14.0}
            {:pos-x 8.0 :v-x 8.0 :desired-v-x 14.0}]
           (acceleration-seq 0.0 140 :pos-x :v-x :desired-v-x "SPEEDX")))))
