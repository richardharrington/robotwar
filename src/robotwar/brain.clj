(ns robotwar.brain
  (:use [clojure.string :only [join]]
        [robotwar.kernel-lexicon]))

(def op-map (zipmap op-commands 
                    (map (fn [op] 
                           (case op
                             "/" #(int (Math/round (float (/ %1 %2))))
                             "#" not=
                             (-> op read-string eval)))
                         op-commands)))

(defn init-register [reg-name read write data]
  {reg-name {:read read
             :write write
             :data data}})

(defn default-read [data world]
  data)

(defn default-write [robot data]
  (assoc-in robot [:brain :registers reg-name] data))

(def default-data 0)

(defn default-register [reg-name]
  (init-register reg-name default-read default-write default-data))

(defn init-brain
  "initialize the brain (meaning all the internal state variables that go along
  with the robot program when it's running).
  (Optionally, also pass in a hash-map of register names and values,
  which will override the defaults)." 
  [program reg-names & [registers]]
  {:acc 0
   :instr-ptr 0
   :call-stack []
   :program program
   :registers (into {} (concat (map default-register reg-names) registers))})

(defn read-register
  "wrapper for the read function in each register. takes a register
  and also takes world state, because
  some of those functions may need access to the world state.
  returns a numeric value."
  [{:keys [read data] :as register} world]
  (read data world))

(defn write-register
  "wrapper for the write function in each register.
  takes a robot, a register, and the data to write.
  returns a full robot (brain and external properties as well).
  TODO: implement extra flag to indicate if we've fired a shot."
  [robot {write :write :as register} data]
  (write robot data))

(defn resolve-arg [{arg-val :val arg-type :type} registers labels world]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (read-register (registers arg-val) world)
    nil))

(defn tick-brain
  "takes a full robot. Only the internal state (the brain) will be 
  different when we pass it back, for all of the operations except 'TO',
  which may alter the external state of the robot as well. (And for the time
  being, shots fired will be indicated with a flag in the robot.)

  Also takes a 'world' parameter, which may contain information that some of the
  registers' read functions may need. Will not be passed out in the return value.

  TODO: Figure out a way to have this function not know about the external robot stuff,
  like that the name of the field leading to the brain is :brain."

  [robot world]
  (let [brain (:brain robot)
        {:keys [acc instr-ptr call-stack registers program]} brain
        [{command :val} arg] ((:instrs program) instr-ptr)
        resolve #(resolve-arg % registers (:labels program) world)
        return-robot #(assoc robot :brain (into brain %))]
    (case command
      "GOTO"             (return-robot {:instr-ptr (resolve arg)})
      "GOSUB"            (return-robot {:instr-ptr (resolve arg)
                                        :call-stack (conj call-stack (inc instr-ptr))})
      "ENDSUB"           (return-robot {:instr-ptr (peek call-stack)
                                        :call-stack (pop call-stack)})
      ("IF", ",")        (return-robot {:instr-ptr (inc instr-ptr)
                                        :acc (resolve arg)})
      ("+" "-" "*" "/")  (return-robot {:instr-ptr (inc instr-ptr)
                                        :acc ((op-map command) acc (resolve arg))})
      ("=" ">" "<" "#")  (if ((op-map command) acc (resolve arg))
                           (return-robot {:instr-ptr (inc instr-ptr)})
                           (return-robot {:instr-ptr (+ instr-ptr 2)}))
      "TO"               (write-register (return-robot {:instr-ptr (inc instr-ptr)})
                                         (registers (:val arg))
                                         acc))))

