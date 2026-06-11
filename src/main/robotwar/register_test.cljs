(ns robotwar.register-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.register :refer [read-register write-register]]
            [robotwar.world :as world]))

(def world (world/init-world ["" ""]))
(def reg-path [:robots 0 :brain :registers])
(def registers (get-in world reg-path))
(def get-registers #(get-in % reg-path))

(deftest register-smoke-test
  (testing "storage/data/random/read-write behavior"
    (let [w1 (write-register (registers "A") world 42)
          r1 (get-registers w1)]
      (is (= 42 (read-register (r1 "A") w1))))
    (let [w1 (write-register (registers "A") world 42)
          r1 (get-registers w1)
          w2 (write-register (r1 "INDEX") w1 1)
          r2 (get-registers w2)
          w3 (write-register (r2 "DATA") w2 100)
          r3 (get-registers w3)]
      (is (= 42 (read-register (r2 "DATA") w2)))
      (is (= 100 (read-register (r3 "A") w3))))
    (let [w1 (assoc-in world [:robots 0 :damage] 50.0)
          w2 (write-register (registers "DAMAGE") w1 25)
          r2 (get-registers w2)]
      (is (= 50 (read-register (r2 "DAMAGE") w2)))
      (is (= 50.0 (get-in w2 [:robots 0 :damage]))))
    (let [w1 (write-register (registers "SPEEDX") world 90)
          r1 (get-registers w1)]
      (is (= 90 (read-register (r1 "SPEEDX") w1)))
      (is (= 9.0 (get-in w1 [:robots 0 :desired-v-x]))))))
