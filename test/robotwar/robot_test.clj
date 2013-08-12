(ns robotwar.robot-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.robot])
  (:require [robotwar.register :as register]
            [robotwar.world :as world]))

(def world (world/init-world 256.0 256.0 [""]))

; The next four tests are pretty much cut and pasted from each other. So sue me.
; To change them significantly, delete three of them and build up from the first one
; again. TODO: write a wrapper for these.

(deftest positive-acceleration-x-test
  (testing "application of SPEEDX register in
           positive direction has expected behavior"
    (let [zeroed-world (assoc-in world [:robots 0 :pos-x] 0.0)
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

(deftest negative-acceleration-x-test
  (testing "application of SPEEDX register in
           negative direction has expected behavior"
    (let [zeroed-world (assoc-in world [:robots 0 :pos-x] 100.0)
          zeroed-registers (get-in world [:robots 0 :brain :registers])
          speedy-world (register/write-register (zeroed-registers "SPEEDX") zeroed-world -140)
          speedy-worlds (world/iterate-worlds speedy-world 1.0)]
      (is (= (take 6 (map (fn [world] 
                            {:pos-x (get-in world [:robots 0 :pos-x]) 
                             :v-x (get-in world [:robots 0 :v-x]) 
                             :desired-v-x (get-in world [:robots 0 :desired-v-x])}) 
                          speedy-worlds))
             [{:pos-x 100.0, :v-x 0.0, :desired-v-x -14.0}
              {:pos-x 98.0, :v-x -4.0, :desired-v-x -14.0}
              {:pos-x 92.0, :v-x -8.0, :desired-v-x -14.0}
              {:pos-x 82.0, :v-x -12.0, :desired-v-x -14.0}
              {:pos-x 68.5, :v-x -14.0, :desired-v-x -14.0}
              {:pos-x 54.5, :v-x -14.0, :desired-v-x -14.0}])))))

(deftest positive-acceleration-y-test
  (testing "application of SPEEDY register in
           positive direction has expected behavior"
    (let [zeroed-world (assoc-in world [:robots 0 :pos-y] 0.0)
          zeroed-registers (get-in world [:robots 0 :brain :registers])
          speedy-world (register/write-register (zeroed-registers "SPEEDY") zeroed-world 140)
          speedy-worlds (world/iterate-worlds speedy-world 1.0)]
      (is (= (take 6 (map (fn [world] 
                            {:pos-y (get-in world [:robots 0 :pos-y]) 
                             :v-y (get-in world [:robots 0 :v-y]) 
                             :desired-v-y (get-in world [:robots 0 :desired-v-y])}) 
                          speedy-worlds))
             [{:pos-y 0.0, :v-y 0.0, :desired-v-y 14.0}
              {:pos-y 2.0, :v-y 4.0, :desired-v-y 14.0}
              {:pos-y 8.0, :v-y 8.0, :desired-v-y 14.0}
              {:pos-y 18.0, :v-y 12.0, :desired-v-y 14.0}
              {:pos-y 31.5, :v-y 14.0, :desired-v-y 14.0}
              {:pos-y 45.5, :v-y 14.0, :desired-v-y 14.0}])))))

(deftest negative-acceleration-y-test
  (testing "application of SPEEDY register in
           negative direction has expected behavior"
    (let [zeroed-world (assoc-in world [:robots 0 :pos-y] 100.0)
          zeroed-registers (get-in world [:robots 0 :brain :registers])
          speedy-world (register/write-register (zeroed-registers "SPEEDY") zeroed-world -140)
          speedy-worlds (world/iterate-worlds speedy-world 1.0)]
      (is (= (take 6 (map (fn [world] 
                            {:pos-y (get-in world [:robots 0 :pos-y]) 
                             :v-y (get-in world [:robots 0 :v-y]) 
                             :desired-v-y (get-in world [:robots 0 :desired-v-y])}) 
                          speedy-worlds))
             [{:pos-y 100.0, :v-y 0.0, :desired-v-y -14.0}
              {:pos-y 98.0, :v-y -4.0, :desired-v-y -14.0}
              {:pos-y 92.0, :v-y -8.0, :desired-v-y -14.0}
              {:pos-y 82.0, :v-y -12.0, :desired-v-y -14.0}
              {:pos-y 68.5, :v-y -14.0, :desired-v-y -14.0}
              {:pos-y 54.5, :v-y -14.0, :desired-v-y -14.0}])))))

