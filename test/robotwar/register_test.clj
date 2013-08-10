(ns robotwar.register-test
  (:require [clojure.test :refer :all]
            [robotwar.register :refer :all]
            [robotwar.world :as world]))

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


;(deftest random-test
;  (testing "push to random register and pull a series of numbers all different
;           from random register"
;    (let [random-register (get-in initial-multi-use-robot [:brain :registers "RANDOM"])
;          new-world (register/write-register random-register initial-multi-use-world 1000)
;          random-nums (repeatedly 5 (partial register/read-register random-register new-world))]
;    (is (= (get-in new-world [:robots 0 :brain :registers "RANDOM" :val])
;           1000))
;    (is (every? #(< -1 % 1000) random-nums))))) 
;
