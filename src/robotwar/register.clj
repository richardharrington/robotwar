(ns robotwar.register)

(def storage-reg-names [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                         "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "Z"])

(def reg-names [ "DATA" 
                 "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                 "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                 "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(defn path-to-robot [robot-idx]
  (:robots robot-idx))

(defn path-to-registers [robot-idx]
 (:robots robot-idx :registers))

(defn path-to-register [robot-idx reg-name]
 (:robots robot-idx :registers reg-name))

(defn path-to-val [robot-idx reg-name]
  (:robots robot-idx :registers reg-name :val))

(defprotocol IRegister
  (read-register [this world])
  (write-register [this world data]))

(defrecord StorageRegister [robot-idx reg-name val]
  IRegister
  (read-register 
    "returns value stored in register"
    [this world]
    val)
  (write-register 
    "returns world with value in register altered"
    [this world data]
    (assoc-in world (path-to-val robot-idx reg-name) data))) 

(defrecord ReadOnlyRegister [robot-idx field-name]
  IRegister
  (read-register 
    "returns the value in a particular robot field" 
    [this world]
    (get-in world (conj (path-to-robot robot-idx) field-name)))
  (write-register 
    "has no effect (returns the world it was given)" 
    [this world data]
    world))

(defn target-register
  "helper function for DataRegister record"
  [world robot-idx index-reg-name]
  (let [registers (get-in world (path-to-registers robot-idx))]
    (registers (reg-names (read-register (registers index-reg-name) world)))))

(defrecord DataRegister [robot-idx index-reg-name]
  IRegister
  (read-register
    "returns the number stored in whatever register 
    is pointed to by the index-reg-name register"
    [this world]
    (read-register (target-register world robot-idx index-reg-name) world))
  (write-register
    "returns a world with the number in the register pointed to 
    by the index-reg-name register updated with the data argument to write-register"
    [this world data]
    (write-register (target-register world robot-idx index-reg-name) world data)))

; TODO: (defrecord RandomRegister [robot-idx reg-name max-rand])

; TODO: (defrecord ShotRegister [robot-idx reg-name])

; TODO: (defrecord RadarRegister [robot-idx reg-name])

;           ; RANDOM
;           (init-register "RANDOM" robot-idx
;                          (fn [world path-to-val]
;                            (rand-int (get-in world path-to-val)))
;                          assoc-in
;                          0)
;
;           ; X and Y and DAMAGE
;           (init-read-only-register "X" robot-idx :pos-x (:pos-x attributes))
;           (init-read-only-register "Y" robot-idx :pos-y (:pos-y attributes))
;           (init-read-only-register "DAMAGE" robot-idx :damage (:damage attributes))])))
;
;           ; TODO: SHOT AND RADAR



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
                      ; TODO: {"RANDOM" (->RandomRegister robot-idx "RANDOM" 0)}
                      ; TODO: {"SHOT"   (->ShotRegister robot-idx "SHOT")}
                      ; TODO: {"RADAR"  (->RadarRegister robot-idx "RADAR")}
                      ]))))

