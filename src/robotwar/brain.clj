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

(defn init-register [reg-name read-func write-func data]
  "the read function should take a world and return a value.
  the write function should take a world and a value and return 
  a new world."
  {reg-name {:read read-func
             :write write-func
             :val data}})

(defn read-register
  "wrapper for the read function in each register. takes a register
  and also takes world state, because
  some of those functions may need access to the world state.
  returns a numeric value."
  [{read :read} world]
  (read world))

(defn write-register
  "wrapper for the write function in each register.
  takes a register, a robot, a world, and the data to write.
  returns a full world."
  [{write :write} world data]
  (write world data))

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

(defn resolve-arg [{arg-val :val arg-type :type} registers labels world]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (read-register (registers arg-val) world)
    nil))

(defn step-brain
  "takes a robot index and a world. Returns a world. 
  
  Read functions may or may not need anything except the brain.
  
  Only the brain (the internal state of the robot)
  will be different when we pass it back, for all of the operations 
  except 'TO', which may also alter the external state of the robot, or the wider world.

  TODO: Figure out a way to have this function not know about the external stuff,
  like that the name of the field leading to the brain is :brain."

  [robot-idx world]
  (let [{brain :brain :as robot} ((:robots world) robot-idx)
        {:keys [acc instr-ptr call-stack registers program]} brain
        [{command :val} arg] ((:instrs program) instr-ptr)
        resolve #(resolve-arg % registers (:labels program) world)
        into-world-brain #(assoc-in world [:robots robot-idx :brain] (into brain %))]
    (case command
      "GOTO"             (into-world-brain {:instr-ptr (resolve arg)})
      "GOSUB"            (into-world-brain {:instr-ptr (resolve arg)
                                            :call-stack (conj call-stack (inc instr-ptr))})
      "ENDSUB"           (into-world-brain {:instr-ptr (peek call-stack)
                                            :call-stack (pop call-stack)})
      ("IF", ",")        (into-world-brain {:instr-ptr (inc instr-ptr)
                                            :acc (resolve arg)})
      ("+" "-" "*" "/")  (into-world-brain {:instr-ptr (inc instr-ptr)
                                            :acc ((op-map command) acc (resolve arg))})
      ("=" ">" "<" "#")  (if ((op-map command) acc (resolve arg))
                           (into-world-brain {:instr-ptr (inc instr-ptr)})
                           (into-world-brain {:instr-ptr (+ instr-ptr 2)}))
      "TO"               (write-register (into-world-brain {:instr-ptr (inc instr-ptr)})
                                         (registers (:val arg))
                                         acc))))

