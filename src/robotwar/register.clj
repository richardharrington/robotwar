(ns robotwar.register
  (:use robotwar.constants))

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

(def robot-field-read-mixin
  ; returns the value of a field in the robot hash-map,
  ; rounded to an integer
  (fn [{:keys [robot-idx field-name multiplier]} world]
    (Math/round (/ (get-in 
                     world 
                     (path-to-robot-field robot-idx field-name)) 
                   multiplier))))

(def robot-field-write-mixin
  ; returns a world with the value of a field in the robot hash map altered
  ; (with the number being cast to floating point before being pushed)
  (fn [{:keys [robot-idx field-name multiplier]} world data]
    (assoc-in 
      world 
      (path-to-robot-field robot-idx field-name)
      (float (* data multiplier)))))

(defrecord StorageRegister [robot-idx reg-name val])
(extend StorageRegister
  IReadRegister 
    {:read-register register-field-read-mixin}
  IWriteRegister 
    {:write-register register-field-write-mixin})

(defrecord ReadWriteRobotFieldRegister [robot-idx field-name multiplier])
(extend ReadWriteRobotFieldRegister
  IReadRegister 
    {:read-register robot-field-read-mixin}
  IWriteRegister 
    {:write-register robot-field-write-mixin})

(defrecord ReadOnlyRobotFieldRegister [robot-idx field-name multiplier])
(extend ReadOnlyRobotFieldRegister
  IReadRegister 
    {:read-register robot-field-read-mixin}
  IWriteRegister 
    ; returns a world with nothing changed
    {:write-register (fn [this world data] 
                       world)})

(defrecord RandomRegister [robot-idx reg-name val])
(extend RandomRegister
  IReadRegister 
    ; returns a random number. maximum value is the :val field of the register
    {:read-register (fn [{val :val} world]
                      (rand-int val))}
  IWriteRegister 
    {:write-register register-field-write-mixin})

(defrecord ShotRegister [robot-idx field-name multiplier])
(extend ShotRegister
  IReadRegister
    {:read-register robot-field-read-mixin}
  IWriteRegister
    ; adds a shell to the list of shells.
    ; It's a no-op if the shot clock hasn't reached zero yet.
    {:write-register 
     (fn [{:keys [robot-idx field-name]} world data]
       (let [{:keys [pos-x pos-y aim shot-timer] :as robot} 
                 (get-in world (path-to-robot robot-idx))]
         (if (> shot-timer 0)
           world
           (let [shells (:shells world)
                 world-with-new-shot-timer (assoc-in
                                             world
                                             (path-to-robot-field robot-idx :shot-timer)
                                             GAME-SECONDS-PER-SHOT)]
             ; TODO: change this next line to something 
             ; that actually releases a shell. This is a
             ; very temporary version to test the shot-timer.
             world-with-new-shot-timer))))})

;(conj shells (shell/new-shell
;               (:pos-x robot)
;               (:pox-y robot)
;               (:aim robot))
;               data))]



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

; TODO: (defrecord ShotRegister [robot-idx reg-name])

; TODO: (defrecord RadarRegister [robot-idx reg-name])

(defn init-registers
  "AIM, INDEX, SPEEDX and SPEEDY.
  AIM and INDEX's specialized behaviors are only when they're used by
  SHOT and DATA, respectively. In themselves, they're only storage registers.
  Likewise, SPEEDX and SPEEDY are used later in step-robot to determine
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
                      {"DATA"   (->DataRegister robot-idx "INDEX")}
                      {"RANDOM" (->RandomRegister robot-idx "RANDOM" 0)}
                      {"SHOT"   (->ShotRegister robot-idx :shot-timer *GAME-SECONDS-PER-TICK*)}
                      ; TODO: {"RADAR"  (->RadarRegister robot-idx "RADAR")}
                      ]))))
