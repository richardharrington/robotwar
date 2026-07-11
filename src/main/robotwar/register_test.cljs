(ns robotwar.register-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [robotwar.constants :refer [ROBOT-RADIUS ROBOT-RANGE-X]]
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

(defn- radar-world [& robot-updates]
  (let [w (world/init-world ["" "" ""])]
    (reduce (fn [acc [idx updates]]
              (update-in acc [:robots idx] merge updates))
            w
            (map-indexed vector robot-updates))))

(defn- read-radar [w dir]
  (let [regs (get-in w [:robots 0 :brain :registers])
        w' (write-register (regs "RADAR") w dir)
        regs' (get-in w' [:robots 0 :brain :registers])]
    (read-register (regs' "RADAR") w')))

(deftest radar-smoke-test
  (testing "ray hits closest alive non-self robot; else returns wall distance"
    (let [robot-hit (radar-world {:pos-x 50.0 :pos-y 128.0}
                                 {:pos-x 150.0 :pos-y 128.0}
                                 {:pos-x 200.0 :pos-y 10.0})]
      (is (= (- (- 100 ROBOT-RADIUS))
             (read-radar robot-hit 90))))
    (let [closer-wins (radar-world {:pos-x 50.0 :pos-y 128.0}
                                   {:pos-x 100.0 :pos-y 128.0}
                                   {:pos-x 200.0 :pos-y 128.0})]
      (is (= (- (- 50 ROBOT-RADIUS))
             (read-radar closer-wins 90))))
    (let [no-hit (radar-world {:pos-x 50.0 :pos-y 128.0}
                              {:pos-x 50.0 :pos-y 200.0}
                              {:pos-x 60.0 :pos-y 220.0})]
      (is (= (- ROBOT-RANGE-X 50)
             (read-radar no-hit 90))))
    (let [dead-excluded (radar-world {:pos-x 50.0 :pos-y 128.0}
                                     {:pos-x 100.0 :pos-y 128.0 :alive? false}
                                     {:pos-x 200.0 :pos-y 10.0})]
      (is (= (- ROBOT-RANGE-X 50)
             (read-radar dead-excluded 90))))))
