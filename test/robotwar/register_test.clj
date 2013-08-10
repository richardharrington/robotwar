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
