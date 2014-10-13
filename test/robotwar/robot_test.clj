(ns robotwar.robot-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.robot]
        [robotwar.constants])
  (:require [robotwar.register :as register]
            [robotwar.world :as world]))

(def world (world/init-world [""]))

(def x
  {:pos-key :pos-x
   :v-key :v-x
   :desired-v-key :desired-v-x
   :register-name-key "SPEEDX"})

(def y
  {:pos-key :pos-y
   :v-key :v-y
   :desired-v-key :desired-v-y
   :register-name-key "SPEEDY"})

(defn acceleration-test [pos speed {:keys [pos-key v-key desired-v-key register-name-key]} expected-sequence]
  (let [zeroed-world (assoc-in world [:robots 0 pos-key] pos)
        zeroed-registers (get-in world [:robots 0 :brain :registers])
        speedy-world (register/write-register (zeroed-registers register-name-key) zeroed-world speed)
        speedy-worlds (iterate (fn [{[robot] :robots :as world}]
                                 (binding [*GAME-SECONDS-PER-TICK* 1.0]
                                   (tick-robot robot world)))
                               speedy-world)]
    (is (= (take 6 (for [{[robot] :robots} speedy-worlds]
                     (select-keys robot [pos-key v-key desired-v-key])))
           expected-sequence))))

(deftest positive-acceleration-x-test
  (testing "application of SPEEDX register in positive direction has expected behavior"
    (acceleration-test
     0.0
     140
     x
     [{:pos-x 0.0, :v-x 0.0, :desired-v-x 14.0}
      {:pos-x 2.0, :v-x 4.0, :desired-v-x 14.0}
      {:pos-x 8.0, :v-x 8.0, :desired-v-x 14.0}
      {:pos-x 18.0, :v-x 12.0, :desired-v-x 14.0}
      {:pos-x 31.5, :v-x 14.0, :desired-v-x 14.0}
      {:pos-x 45.5, :v-x 14.0, :desired-v-x 14.0}])))

(deftest negative-acceleration-x-test
  (testing "application of SPEEDX register in negative direction has expected behavior"
    (acceleration-test
     100.0
     -140
     x
     [{:pos-x 100.0, :v-x 0.0, :desired-v-x -14.0}
      {:pos-x 98.0, :v-x -4.0, :desired-v-x -14.0}
      {:pos-x 92.0, :v-x -8.0, :desired-v-x -14.0}
      {:pos-x 82.0, :v-x -12.0, :desired-v-x -14.0}
      {:pos-x 68.5, :v-x -14.0, :desired-v-x -14.0}
      {:pos-x 54.5, :v-x -14.0, :desired-v-x -14.0}])))

(deftest positive-acceleration-y-test
  (testing "application of SPEEDY register in positive direction has expected behavior"
    (acceleration-test
     0.0
     140
     y
     [{:pos-y 0.0, :v-y 0.0, :desired-v-y 14.0}
      {:pos-y 2.0, :v-y 4.0, :desired-v-y 14.0}
      {:pos-y 8.0, :v-y 8.0, :desired-v-y 14.0}
      {:pos-y 18.0, :v-y 12.0, :desired-v-y 14.0}
      {:pos-y 31.5, :v-y 14.0, :desired-v-y 14.0}
      {:pos-y 45.5, :v-y 14.0, :desired-v-y 14.0}])))

(deftest negative-acceleration-y-test
  (testing "application of SPEEDY register in negative direction has expected behavior"
    (acceleration-test
     100.0
     -140
     y
     [{:pos-y 100.0, :v-y 0.0, :desired-v-y -14.0}
      {:pos-y 98.0, :v-y -4.0, :desired-v-y -14.0}
      {:pos-y 92.0, :v-y -8.0, :desired-v-y -14.0}
      {:pos-y 82.0, :v-y -12.0, :desired-v-y -14.0}
      {:pos-y 68.5, :v-y -14.0, :desired-v-y -14.0}
      {:pos-y 54.5, :v-y -14.0, :desired-v-y -14.0}])))
