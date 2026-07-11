(ns robotwar.register
  (:require [robotwar.constants :refer [*GAME-SECONDS-PER-TICK* GAME-SECONDS-PER-SHOT
                                        ROBOT-RADIUS ROBOT-RANGE-X ROBOT-RANGE-Y]]
            [robotwar.physics :as physics]
            [robotwar.shell :as shell]))

(def reg-names [ "DATA"
                 "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M"
                 "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                 "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(defn path-to-robot [robot-idx]
  [:robots robot-idx])

(defn path-to-robot-field [robot-idx robot-field]
  [:robots robot-idx robot-field])

(defn path-to-registers [robot-idx]
  [:robots robot-idx :brain :registers])

(defn path-to-val [robot-idx reg-name]
  [:robots robot-idx :brain :registers reg-name :val])

(defprotocol IReadRegister
  "returns the value of a register"
  (read-register [this world]))

(defprotocol IWriteRegister
  "returns a world"
  (write-register [this world data]))

(def register-field-read-mixin
  ; returns :val field of register
  (fn [{val :val} world]
    val))

(def register-field-write-mixin
  ; returns a world with :val field of register altered
  (fn [{:keys [robot-idx reg-name]} world data]
    (assoc-in world
              (path-to-val robot-idx reg-name)
              data)))

(defn rw-round [x]
  #?(:clj (Math/round x)
     :cljs (js/Math.round x)))

(def robot-field-read-mixin
  ; returns the value of a field in the robot hash-map,
  ; rounded to an integer
  (fn [{:keys [robot-idx field-name multiplier]} world]
    (rw-round (/ (get-in
                 world
                 (path-to-robot-field robot-idx field-name))
               multiplier))))

(def robot-field-write-mixin
  ; returns a world with the value of a field in the robot hash map altered
  ; (with the number being cast to double before being pushed)
  (fn [{:keys [robot-idx field-name multiplier]} world data]
    (assoc-in
      world
      (path-to-robot-field robot-idx field-name)
      (double (* data multiplier)))))

(defrecord StorageRegister [robot-idx reg-name val])
(defrecord ReadWriteRobotFieldRegister [robot-idx field-name multiplier])
(defrecord ReadOnlyRobotFieldRegister [robot-idx field-name multiplier])
(defrecord RandomRegister [robot-idx reg-name val])
(defrecord AimRegister [robot-idx field-name multiplier])
(defrecord ShotRegister [robot-idx field-name multiplier])
(defrecord RadarRegister [robot-idx reg-name val])

(defn- radar-scan
  "cast a ray from the firing robot's center along the direction stored in
  the RADAR register (degrees, robotwar convention). Returns the rounded
  signed distance: negative if the closest hit is another alive robot's
  disc, otherwise the positive distance to the arena wall the ray exits."
  [{:keys [robot-idx val]} world]
  (let [{px :pos-x py :pos-y} (get-in world (path-to-robot robot-idx))
        {dx :x dy :y} (physics/decompose-angle val)
        robot-hits (keep (fn [other]
                           (when (and (:alive? other)
                                      (not= (:idx other) robot-idx))
                             (physics/ray-disc-hit-distance
                               px py dx dy
                               (:pos-x other) (:pos-y other)
                               ROBOT-RADIUS)))
                         (:robots world))
        closest-robot (when (seq robot-hits) (apply min robot-hits))
        wall-dist (physics/ray-arena-exit-distance
                    px py dx dy ROBOT-RANGE-X ROBOT-RANGE-Y)]
    (rw-round (if closest-robot (- closest-robot) wall-dist))))

#?(:clj
   (do
     (extend StorageRegister
       IReadRegister {:read-register register-field-read-mixin}
       IWriteRegister {:write-register register-field-write-mixin})
     (extend ReadWriteRobotFieldRegister
       IReadRegister {:read-register robot-field-read-mixin}
       IWriteRegister {:write-register robot-field-write-mixin})
     (extend ReadOnlyRobotFieldRegister
       IReadRegister {:read-register robot-field-read-mixin}
       IWriteRegister {:write-register (fn [this world data] world)})
     (extend RandomRegister
       IReadRegister {:read-register (fn [{val :val} world] (rand-int val))}
       IWriteRegister {:write-register register-field-write-mixin})
     (extend AimRegister
       IReadRegister {:read-register robot-field-read-mixin}
       IWriteRegister {:write-register (fn [{:keys [robot-idx field-name multiplier]} world data]
                                        (assoc-in world
                                                  (path-to-robot-field robot-idx field-name)
                                                  (mod (double (* data multiplier)) 360)))})
     (extend ShotRegister
       IReadRegister {:read-register robot-field-read-mixin}
       IWriteRegister {:write-register (fn [{:keys [robot-idx field-name]}
                                            {:keys [shells next-shell-id] :as world}
                                            data]
                                        (let [{:keys [pos-x pos-y aim shot-timer] :as robot}
                                              (get-in world (path-to-robot robot-idx))]
                                          (if (> shot-timer 0)
                                            world
                                            (let [world-with-new-shot-timer
                                                  (assoc-in world
                                                            (path-to-robot-field robot-idx :shot-timer)
                                                            GAME-SECONDS-PER-SHOT)]
                                              (assoc world-with-new-shot-timer
                                                     :shells (assoc shells next-shell-id (shell/init-shell pos-x pos-y aim next-shell-id data))
                                                     :next-shell-id (inc next-shell-id))))))})
     (extend RadarRegister
       IReadRegister {:read-register radar-scan}
       IWriteRegister {:write-register (fn [{:keys [robot-idx reg-name]} world data]
                                         (assoc-in world
                                                   (path-to-val robot-idx reg-name)
                                                   (mod (double data) 360)))}))
   :cljs
   (do
     (extend-type StorageRegister
       IReadRegister (read-register [this world] (register-field-read-mixin this world))
       IWriteRegister (write-register [this world data] (register-field-write-mixin this world data)))
     (extend-type ReadWriteRobotFieldRegister
       IReadRegister (read-register [this world] (robot-field-read-mixin this world))
       IWriteRegister (write-register [this world data] (robot-field-write-mixin this world data)))
     (extend-type ReadOnlyRobotFieldRegister
       IReadRegister (read-register [this world] (robot-field-read-mixin this world))
       IWriteRegister (write-register [this world data] world))
     (extend-type RandomRegister
       IReadRegister (read-register [this world] (rand-int (:val this)))
       IWriteRegister (write-register [this world data] (register-field-write-mixin this world data)))
     (extend-type AimRegister
       IReadRegister (read-register [this world] (robot-field-read-mixin this world))
       IWriteRegister (write-register [this world data]
                       (assoc-in world
                                 (path-to-robot-field (:robot-idx this) (:field-name this))
                                 (mod (double (* data (:multiplier this))) 360))))
     (extend-type ShotRegister
       IReadRegister (read-register [this world] (robot-field-read-mixin this world))
       IWriteRegister (write-register [this {:keys [shells next-shell-id] :as world} data]
                       (let [{:keys [pos-x pos-y aim shot-timer] :as robot}
                             (get-in world (path-to-robot (:robot-idx this)))]
                         (if (> shot-timer 0)
                           world
                           (let [world-with-new-shot-timer
                                 (assoc-in world
                                           (path-to-robot-field (:robot-idx this) :shot-timer)
                                           GAME-SECONDS-PER-SHOT)]
                             (assoc world-with-new-shot-timer
                                    :shells (assoc shells next-shell-id (shell/init-shell pos-x pos-y aim next-shell-id data))
                                    :next-shell-id (inc next-shell-id)))))))
     (extend-type RadarRegister
       IReadRegister (read-register [this world] (radar-scan this world))
       IWriteRegister (write-register [this world data]
                       (assoc-in world
                                 (path-to-val (:robot-idx this) (:reg-name this))
                                 (mod (double data) 360))))))

(defn get-target-register
  "helper function for DataRegister record"
  [world robot-idx index-reg-name]
  (let [registers (get-in world (path-to-registers robot-idx))]
    (registers (reg-names (read-register (registers index-reg-name) world)))))

(defrecord DataRegister [robot-idx index-reg-name]
  IReadRegister
    (read-register
      ; returns the number stored in whatever register
      ; is pointed to by the index-reg-name register
      [this world]
      (read-register (get-target-register world robot-idx index-reg-name) world))
  IWriteRegister
    (write-register
      ; returns a world with the number in the register pointed to
      ; by the index-reg-name register updated with the data argument to write-register
      [this world data]
      (write-register (get-target-register world robot-idx index-reg-name) world data)))

(defn init-registers
  "AIM, INDEX, SPEEDX and SPEEDY.
  AIM and INDEX's specialized behaviors are only when they're used by
  SHOT and DATA, respectively. In themselves, they're only storage registers.
  Likewise, SPEEDX and SPEEDY are used later in tick-robot to determine
  the appropriate acceleration, which may have to applied over several ticks."
  [robot-idx]
  (let [storage-registers (for [reg-name [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L"
                                          "M" "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "Z"]]
                            {reg-name (->StorageRegister robot-idx reg-name 0)})
        read-only-registers (for [[reg-name robot-field mult] [["X"      :pos-x  1.0]
                                                               ["Y"      :pos-y  1.0]
                                                               ["DAMAGE" :damage 1.0]]]
                              {reg-name (->ReadOnlyRobotFieldRegister robot-idx robot-field mult)})
        ; TODO: change reading from these registers into an error, instead of just a wasted
        ; processor cyle for the robot.
        read-write-registers (for [[reg-name robot-field mult] [["AIM"    :aim         1.0]
                                                                ["SPEEDX" :desired-v-x 0.1]
                                                                ["SPEEDY" :desired-v-y 0.1]]]
                               {reg-name (->ReadWriteRobotFieldRegister robot-idx robot-field mult)})]
    (into {} (concat storage-registers
                     read-only-registers
                     read-write-registers
                     [{"INDEX"  (->StorageRegister robot-idx "INDEX" 0)}
                      {"DATA"   (->DataRegister    robot-idx "INDEX")}
                      {"RANDOM" (->RandomRegister  robot-idx "RANDOM" 0)}
                      {"AIM"    (->AimRegister     robot-idx :aim 1.0)}
                      {"SHOT"   (->ShotRegister    robot-idx :shot-timer *GAME-SECONDS-PER-TICK*)}
                      {"RADAR"  (->RadarRegister   robot-idx "RADAR" 0)}]))))
