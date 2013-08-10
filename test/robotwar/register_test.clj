(ns robotwar.register-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.register])
  (:require [robotwar.world :as world]))

(def world (world/init-world 256 256 [""]))
(def robot-path [:robots 0])
(def reg-path [:robots 0 :brain :registers])
(def registers (get-in world reg-path))

(deftest storage-register-test
  (testing "can write and read to storage register's :val field"
    (let [new-world (write-register (registers "A") world 42)
          new-registers (get-in new-world reg-path)]
      (is (= (read-register (new-registers "A") new-world)
             42))
      (is (= (get-in new-registers ["A" :val])
             42))))) 

(deftest index-data-pair-test
  (testing "registers whose index numbers are push to INDEX can
           be referenced by accessing DATA"
    (let [world1 (write-register (registers "A") world 42)
          registers1 (get-in world1 reg-path)
          world2 (write-register (registers1 "INDEX") world1 1)
          registers2 (get-in world2 reg-path)
          world3 (write-register (registers2 "DATA") world2 100)
          registers3 (get-in world3 reg-path)]
      (is (= (read-register (registers2 "DATA") world2)
             42))
      (is (= (read-register (registers3 "A") world3)
             100)))))

(deftest random-test
  (testing "write to random register's :val field, 
           and read a series of numbers all different
           from random register"
    (let [new-world (write-register (registers "RANDOM") world 1000)
          new-registers (get-in new-world reg-path)
          random-nums (repeatedly 5 (partial read-register (new-registers "RANDOM") new-world))]
      (is (= (get-in new-registers ["RANDOM" :val])
             1000))
      (is (every? #(< -1 % 1000) random-nums))
      (is (apply not= random-nums)))))

