(ns robotwar.register)

(def reg-names [ "DATA" 
                 "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                 "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                 "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(defn path-to-robot [robot-idx]
  [:robots robot-idx])

(defn path-to-brain [robot-idx]
  [:robots robot-idx :brain])

(defn path-to-registers [robot-idx]
  [:robots robot-idx :brain :registers])

(defn path-to-register [robot-idx reg-name]
  [:robots robot-idx :brain :registers reg-name])

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
  {:read-register (fn [this world]
                    (:val this))})

(def register-field-write-mixin
  ; returns a world with :val field of register altered
  {:write-register (fn [this world data]
                     (assoc-in world 
                               (path-to-val (:robot-idx this) (:reg-name this))
                               data))})

(def robot-field-read-mixin
  ; returns the value of a field in the robot hash-map
  {:read-register (fn [this world]
                    (get-in 
                      world 
                      (conj (path-to-robot (:robot-idx this)) (:field-name this))))})

(def robot-field-write-mixin
  ; returns a world with the value of a field in the robot hash map altered
  {:write-register (fn [this world data]
                     (assoc-in 
                       world 
                       (conj (path-to-robot (:robot-idx this)) (:field-name this)) 
                       data))})

(def no-op-write-mixin
  ; returns a world with nothing changed
  {:write-register (fn [this world data] 
                     world)})

(def random-read-mixin
  ; returns a random number. maximum value is the :val field of the register
  {:read-register (fn [this world]
                    (rand-int (:val this)))})

(defrecord StorageRegister [robot-idx reg-name val])
(extend StorageRegister
  IReadRegister register-field-read-mixin
  IWriteRegister register-field-write-mixin)

(defrecord ReadWriteRobotFieldRegister [robot-idx field-name])
(extend ReadWriteRobotFieldRegister
  IReadRegister robot-field-read-mixin
  IWriteRegister robot-field-write-mixin)

(defrecord ReadOnlyRobotFieldRegister [robot-idx field-name])
(extend ReadOnlyRobotFieldRegister
  IReadRegister robot-field-read-mixin
  IWriteRegister no-op-write-mixin)

(defrecord RandomRegister [robot-idx reg-name val])
(extend RandomRegister
  IReadRegister random-read-mixin
  IWriteRegister register-field-write-mixin)

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
        read-only-registers (for [[reg-name robot-field] [["X"      :pos-x]
                                                          ["Y"      :pos-y]
                                                          ["DAMAGE" :damage]]]
                              {reg-name (->ReadOnlyRobotFieldRegister robot-idx robot-field)})
        ; TODO: change reading from these registers into an error, instead of just a wasted
        ; processor cyle for the robot.
        read-write-registers (for [[reg-name robot-field] [["AIM"    :aim]
                                                           ["SPEEDX" :v-x]
                                                           ["SPEEDY" :v-y]]]
                               {reg-name (->ReadWriteRobotFieldRegister robot-idx robot-field)})]
    (into {} (concat storage-registers 
                     read-only-registers
                     read-write-registers
                     [{"INDEX"  (->StorageRegister robot-idx "INDEX" 0)}
                      {"DATA"   (->DataRegister robot-idx "INDEX")}
                      {"RANDOM" (->RandomRegister robot-idx "RANDOM" 0)}
                      ; TODO: {"SHOT"   (->ShotRegister robot-idx "SHOT")}
                      ; TODO: {"RADAR"  (->RadarRegister robot-idx "RADAR")}
                      ]))))
