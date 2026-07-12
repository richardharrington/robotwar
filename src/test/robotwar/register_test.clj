(ns robotwar.register-test
  (:require [clojure.test :refer [deftest is testing]]
            [robotwar.constants :refer [ROBOT-RADIUS ROBOT-RANGE-X]]
            [robotwar.register :refer [read-register write-register]]
            [robotwar.world :as world]))

(def world (world/init-world ["" ""]))
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
    (let [world1 (assoc-in world [:robots 0 :damage] 50.0)
          registers1 (get-registers world1)
          world2 (write-register (registers "DAMAGE") world1 25)
          registers2 (get-registers world2)]
      (is (= (read-register (registers1 "DAMAGE") world1)
             50))
      (is (= (read-register (registers2 "DAMAGE") world2)
             50))
      (is (= (get-in world2 [:robots 0 :damage])
             50.0)))))

(deftest read-write-test
  (testing "can read and write from registers that are interfaces
           for robot fields, and also those robot fields get written to"
    (let [new-world (write-register (registers "SPEEDX") world 90)
          new-registers (get-registers new-world)]
      (is (= (read-register (new-registers "SPEEDX") new-world)
             90))
      (is (= (get-in new-world [:robots 0 :desired-v-x])
             9.0)))))

(defn- radar-world
  "build a two-or-three-robot world with fixed positions/alive states
  suitable for RADAR geometry tests."
  [& robot-updates]
  (let [w (world/init-world ["" "" ""])]
    (reduce (fn [acc [idx updates]]
              (update-in acc [:robots idx] merge updates))
            w
            (map-indexed vector robot-updates))))

(defn- read-radar
  "aim robot 0's RADAR at `dir` degrees and return the resulting read."
  [w dir]
  (let [regs (get-in w [:robots 0 :brain :registers])
        w' (write-register (regs "RADAR") w dir)
        regs' (get-in w' [:robots 0 :brain :registers])]
    (read-register (regs' "RADAR") w')))

(deftest radar-hits-robot-returns-negative-distance-test
  (testing "ray along +x hitting a robot 100 units away returns
           -(100 - ROBOT-RADIUS) since distance is to the disc entry point"
    (let [w (radar-world {:pos-x 50.0 :pos-y 128.0}
                         {:pos-x 150.0 :pos-y 128.0}
                         {:pos-x 200.0 :pos-y 10.0})]
      ; RW aim 90° = clojure 0° → direction (1, 0), i.e. +x
      (is (= (long (- (- 100 ROBOT-RADIUS)))
             (read-radar w 90))))))

(deftest radar-closest-robot-wins-test
  (testing "with two robots along the ray, the closer one is returned"
    (let [w (radar-world {:pos-x 50.0 :pos-y 128.0}
                         {:pos-x 100.0 :pos-y 128.0}   ; 50 units away
                         {:pos-x 200.0 :pos-y 128.0})] ; 150 units away
      (is (= (long (- (- 50 ROBOT-RADIUS)))
             (read-radar w 90))))))

(deftest radar-no-robots-returns-wall-distance-test
  (testing "ray with no robot in its path returns the positive wall distance"
    (let [w (radar-world {:pos-x 50.0 :pos-y 128.0}
                         {:pos-x 50.0 :pos-y 200.0}   ; not on the +x ray
                         {:pos-x 60.0 :pos-y 220.0})] ; not on the +x ray
      ; +x from x=50 → hits x=ROBOT-RANGE-X at distance ROBOT-RANGE-X - 50
      (is (= (long (- ROBOT-RANGE-X 50))
             (read-radar w 90))))))

(deftest radar-self-not-detected-test
  (testing "robot 0's radar does not see robot 0"
    (let [w (radar-world {:pos-x 50.0 :pos-y 128.0}
                         {:pos-x 50.0 :pos-y 200.0}     ; not on ray
                         {:pos-x 60.0 :pos-y 220.0})]   ; not on ray
      ; the only robot on the +x ray from (50, 128) would be robot 0 itself
      (is (pos? (read-radar w 90))))))

(deftest radar-dead-robots-excluded-test
  (testing "dead robots do not block or return a radar hit"
    (let [w (radar-world {:pos-x 50.0 :pos-y 128.0}
                         {:pos-x 100.0 :pos-y 128.0 :alive? false}
                         {:pos-x 200.0 :pos-y 10.0})]
      ; even though robot 1 is directly in front of robot 0, it's dead
      (is (= (long (- ROBOT-RANGE-X 50))
             (read-radar w 90))))))

(deftest radar-ray-along-negative-y-test
  (testing "RW aim 0° points along -y (up); robot above is detected"
    (let [w (radar-world {:pos-x 128.0 :pos-y 128.0}
                         {:pos-x 128.0 :pos-y 100.0}       ; 28 units above
                         {:pos-x 200.0 :pos-y 200.0})]
      (is (= (long (- (- 28 ROBOT-RADIUS)))
             (read-radar w 0))))))

(deftest radar-write-mods-360-test
  (testing "writing to RADAR stores the direction mod 360"
    (let [regs (get-in world [:robots 0 :brain :registers])
          w1 (write-register (regs "RADAR") world 450)
          regs1 (get-in w1 [:robots 0 :brain :registers])]
      (is (= 90.0 (:val (regs1 "RADAR")))))))
