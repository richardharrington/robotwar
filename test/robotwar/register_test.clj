(ns robotwar.register-test
  (:use [clojure.test]
        [midje.sweet]
        [robotwar.register])
  (:require [robotwar.world :as world]))

(def world (world/init-world 256 256 [""]))
(def robot-path [:robots 0])
(def reg-path [:robots 0 :brain :registers])
(def registers (get-in world reg-path))
(def get-registers #(get-in % reg-path))

(deftest storage-register-test
  (testing "can write and read to storage register's :val field"
    (let [new-world (write-register (registers "A") world 42)
          new-registers (get-registers new-world)]
      (is (= (read-register (new-registers "A") new-world)
             42))
      (is (= (get-in new-registers ["A" :val])
             42))))) 

(deftest index-data-pair-test
  (testing "registers whose index numbers are push to INDEX can
           be referenced by accessing DATA"
    (let [world1 (write-register (registers "A") world 42)
          registers1 (get-registers world1)
          world2 (write-register (registers1 "INDEX") world1 1)
          registers2 (get-registers world2)
          world3 (write-register (registers2 "DATA") world2 100)
          registers3 (get-registers world3)]
      (is (= (read-register (registers2 "DATA") world2)
             42))
      (is (= (read-register (registers3 "A") world3)
             100)))))

(deftest random-test
  (testing "write to random register's :val field, 
           and read a series of numbers all different
           from random register"
    (let [new-world (write-register (registers "RANDOM") world 1000)
          new-registers (get-registers new-world)
          random-nums (repeatedly 5 (partial read-register (new-registers "RANDOM") new-world))]
      (is (= (get-in new-registers ["RANDOM" :val])
             1000))
      (is (every? #(< -1 % 1000) random-nums))
      (is (apply not= random-nums)))))

(deftest read-only-test
  (testing "can read from read-only registers, but not write to them
           (and also the robot fields don't get written to)"
    (let [world1 (assoc-in world [:robots 0 :damage] 50)
          registers1 (get-registers world1)
          world2 (write-register (registers "DAMAGE") world1 25)
          registers2 (get-registers world2)]
      (is (= (read-register (registers1 "DAMAGE") world1)
             50))
      (is (= (read-register (registers2 "DAMAGE") world2)
             50))
      (is (= (get-in world2 [:robots 0 :damage])
             50)))))
