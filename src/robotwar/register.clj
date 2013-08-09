(ns robotwar.register)

(def storage-reg-names [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                         "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "Z"])

(def reg-names [ "DATA" 
                 "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                 "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                 "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(defn path-to-robot [robot-idx]
  [:robots robot-idx])

(defn path-to-registers [robot-idx]
  [:robots robot-idx :registers])

(defn path-to-register [robot-idx reg-name]
  [:robots robot-idx :registers reg-name])

(defn path-to-val [robot-idx reg-name]
  [:robots robot-idx :registers reg-name :val])

(defprotocol IReadRegister
  "returns the value of a register"
  (read-register [this world]))

(defprotocol IWriteRegister
  "returns a world"
  (write-register [this world data]))

(def default-read-mixin
  ; returns :val field of register
  {:read-register (fn [this world]
                    (:val this))})

(def default-write-mixin
  ; returns a world with :val field of register altered
  {:write-register (fn [this world data]
                     (assoc-in world 
                               (path-to-val (:robot-idx this) (:reg-name this))
                               data))})

(def robot-field-read-mixin
  ; returns the value of a field in the robot hash-map
  {:read-register (fn [this world]
                    (get-in world (conj path-to-robot (:field-name this))))})

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
  IReadRegister default-read-mixin
  IWriteRegister default-write-mixin)

(defrecord ReadOnlyRegister [robot-idx field-name])
(extend ReadOnlyRegister
  IReadRegister robot-field-read-mixin
  IWriteRegister no-op-write-mixin)

(defrecord RandomRegister [robot-idx reg-name val])
(extend RandomRegister
  IReadRegister random-read-mixin
  IWriteRegister default-write-mixin)

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
  [robot-idx attributes]
  (let [storage-registers (for [reg-name storage-reg-names]
                            {reg-name (->StorageRegister robot-idx reg-name 0)})
        faux-storage-registers (for [reg-name ["AIM" "INDEX" "SPEEDX" "SPEEDY"]]
                                 {reg-name (->StorageRegister robot-idx reg-name 0)})]
    (into {} (concat storage-registers 
                     faux-storage-registers
                     [{"X"      (->ReadOnlyRegister robot-idx :pos-x)}
                      {"Y"      (->ReadOnlyRegister robot-idx :pos-y)}
                      {"DAMAGE" (->ReadOnlyRegister robot-idx :damage)}
                      {"DATA"   (->DataRegister robot-idx "INDEX")}
                      {"RANDOM" (->RandomRegister robot-idx "RANDOM" 0)}
                      ; TODO: {"SHOT"   (->ShotRegister robot-idx "SHOT")}
                      ; TODO: {"RADAR"  (->RadarRegister robot-idx "RADAR")}
                      ]))))

