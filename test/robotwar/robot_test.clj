(ns robotwar.robot-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.robot])
  (:require [robotwar.register :as register]
            [robotwar.world :as world]))

(def world (world/init-world 256.0 256.0 [""]))

(deftest positive-acceleration-test
  (testing "application of SPEEDX register in
           positive direction has expected behavior"
    (let [zeroed-world (assoc-in world [:robots 0 :pos-x] 0)
          zeroed-registers (get-in world [:robots 0 :brain :registers])
          speedy-world (register/write-register (zeroed-registers "SPEEDX") zeroed-world 140)
          speedy-worlds (world/iterate-worlds speedy-world 1.0)]
      (is (= (take 6 (map (fn [world] 
                            {:pos-x (get-in world [:robots 0 :pos-x]) 
                             :v-x (get-in world [:robots 0 :v-x]) 
                             :desired-v-x (get-in world [:robots 0 :desired-v-x])}) 
                          speedy-worlds))
             [{:pos-x 0.0, :v-x 0.0, :desired-v-x 14.0}
              {:pos-x 2.0, :v-x 4.0, :desired-v-x 14.0}
              {:pos-x 8.0, :v-x 8.0, :desired-v-x 14.0}
              {:pos-x 18.0, :v-x 12.0, :desired-v-x 14.0}
              {:pos-x 31.5, :v-x 14.0, :desired-v-x 14.0}
              {:pos-x 45.5, :v-x 14.0, :desired-v-x 14.0}])))))

