(ns robotwar.world-test
  (:require [clojure.test :refer :all]
            [robotwar.world :refer :all]
            [robotwar.robot :as robot]
            [robotwar.constants :refer [MAX-BLAST-DAMAGE BLAST-RADIUS]]))

(defn- make-test-robot
  [idx pos-x pos-y damage]
  (robot/init-robot idx "" {:pos-x pos-x :pos-y pos-y :aim 0.0 :damage damage}))

(defn- make-test-shell
  [id pos-x pos-y]
  {:id id :pos-x pos-x :pos-y pos-y :v-x 0.0 :v-y 0.0 :dest-x pos-x :dest-y pos-y :exploded false})

(deftest init-world-valid-counts-test
  (testing "init-world succeeds with 2 to 5 programs"
    (is (= 2 (count (:robots (init-world ["" ""])))))
    (is (= 3 (count (:robots (init-world ["" "" ""])))))
    (is (= 5 (count (:robots (init-world ["" "" "" "" ""])))))))

(deftest init-world-alive-test
  (testing "all robots start alive"
    (let [world (init-world ["" ""])]
      (is (every? :alive? (:robots world))))))

(deftest init-world-too-few-test
  (testing "init-world throws with fewer than 2 programs"
    (is (thrown? clojure.lang.ExceptionInfo
                 (init-world [""])))
    (let [ex (try (init-world [""])
                  (catch clojure.lang.ExceptionInfo e e))]
      (when ex
        (is (= :insufficient-programs (-> ex ex-data :error)))))))

(deftest init-world-too-many-test
  (testing "init-world throws with more than 5 programs"
    (is (thrown? clojure.lang.ExceptionInfo
                 (init-world ["" "" "" "" "" ""])))
    (let [ex (try (init-world ["" "" "" "" "" ""])
                  (catch clojure.lang.ExceptionInfo e e))]
      (when ex
        (is (= :too-many-programs (-> ex ex-data :error)))))))

(deftest tick-combined-world-skips-dead-robots-test
  (testing "tick-combined-world does not tick dead robots"
    (let [world (init-world ["" "90 TO AIM"])
          dead-world (assoc-in world [:robots 0 :alive?] false)
          next-world (tick-combined-world dead-world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?])))
      (is (= 0 (get-in next-world [:robots 0 :brain :instr-ptr])))
      (is (= 1 (get-in next-world [:robots 1 :brain :instr-ptr]))))))

(deftest shell-damage-direct-hit-test
  (testing "a shell exploding directly on a robot deals MAX-BLAST-DAMAGE"
    (let [world {:shells {0 (make-test-shell 0 100.0 100.0)}
                 :robots [(make-test-robot 0 100.0 100.0 100.0)]
                 :next-shell-id 1}
          next-world (tick-combined-world world)]
      (is (= 70.0 (get-in next-world [:robots 0 :damage]))))))

(defn- approx= [a b epsilon]
  (< (Math/abs (- a b)) epsilon))

(deftest shell-damage-at-distance-test
  (testing "shell damage follows quadratic falloff curve"
    (let [shell (make-test-shell 0 100.0 100.0)
          test-case (fn [distance expected-damage]
                      (let [robot (make-test-robot 0 (+ 100.0 distance) 100.0 100.0)
                            world {:shells {0 shell} :robots [robot] :next-shell-id 1}
                            next-world (tick-combined-world world)]
                        (is (approx= (- 100.0 expected-damage)
                                     (get-in next-world [:robots 0 :damage])
                                     1e-10)
                            (str "distance=" distance))))]
      (test-case 0.0 MAX-BLAST-DAMAGE)
      (test-case 7.0 (* MAX-BLAST-DAMAGE (/ 4.0 9.0)))
      (test-case 14.0 (* MAX-BLAST-DAMAGE (/ 1.0 9.0)))
      (test-case 21.0 0.0)
      (test-case 25.0 0.0))))

(deftest shell-damage-stacking-test
  (testing "multiple shells exploding in the same tick stack damage additively"
    (let [world {:shells {0 (make-test-shell 0 100.0 100.0)
                          1 (make-test-shell 1 100.0 100.0)}
                 :robots [(make-test-robot 0 100.0 100.0 100.0)]
                 :next-shell-id 2}
          next-world (tick-combined-world world)]
      (is (= 40.0 (get-in next-world [:robots 0 :damage]))))))

(deftest shell-damage-self-damage-test
  (testing "a robot can be damaged by its own shell"
    (let [world {:shells {0 (make-test-shell 0 100.0 100.0)}
                 :robots [(make-test-robot 0 100.0 100.0 100.0)]
                 :next-shell-id 1}
          next-world (tick-combined-world world)]
      (is (= 70.0 (get-in next-world [:robots 0 :damage]))))))

(deftest shell-damage-skips-dead-robots-test
  (testing "dead robots are not damaged by shell explosions"
    (let [world {:shells {0 (make-test-shell 0 100.0 100.0)}
                 :robots [(assoc (make-test-robot 0 200.0 200.0 100.0) :alive? false)
                          (make-test-robot 1 100.0 100.0 100.0)]
                 :next-shell-id 1}
          next-world (tick-combined-world world)]
      (is (= 100.0 (get-in next-world [:robots 0 :damage])))
      (is (= 70.0 (get-in next-world [:robots 1 :damage]))))))

(deftest robot-death-threshold-test
  (testing "robot with damage exactly 0 is marked dead after tick"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 0.0)
                                     (make-test-robot 1 200.0 200.0 100.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?])))))

  (testing "robot with negative damage is marked dead after tick"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 -5.0)
                                     (make-test-robot 1 200.0 200.0 100.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (= false (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?])))))

  (testing "robot with positive damage stays alive after tick"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 1.0)
                                     (make-test-robot 1 200.0 200.0 100.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (= true (get-in next-world [:robots 0 :alive?])))
      (is (= true (get-in next-world [:robots 1 :alive?]))))))

(deftest victory-result-test
  (testing "one alive robot produces winner result"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 0.0)
                                     (make-test-robot 1 200.0 200.0 100.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (= {:winner 1} (:result next-world)))))

  (testing "zero alive robots produces tie result with just-died indices"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 0.0)
                                     (make-test-robot 1 200.0 200.0 0.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)
          result (:result next-world)]
      (is (nil? (:winner result)))
      (is (= {:game-over? true} result))
      (is (= #{0 1} (set (:just-died next-world))))))

  (testing "multiple alive robots produces nil result"
    (let [world {:shells {} :robots [(make-test-robot 0 100.0 100.0 100.0)
                                     (make-test-robot 1 200.0 200.0 100.0)]
                 :next-shell-id 0}
          next-world (tick-combined-world world)]
      (is (nil? (:result next-world))))))
