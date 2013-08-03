(ns robotwar.brain
  (:use [clojure.string :only [join]]
        [clojure.pprint :only [pprint]]
        [robotwar.kernel-lexicon]))

(def op-map (into {} (for [op robotwar.kernel-lexicon/op-commands]
                       [op (case op
                             "/" #(int (Math/round (float (/ %1 %2))))
                             "#" not=
                             (-> op read-string eval))])))

(defn read-register
  "a function to query the robot housing this brain, for information
  from the registers. takes a reg-name, a robot and a world,
  and returns the running of the register's read function on the world."
  [{read :read} world]
    (read world))  

(defn write-register
  "a function to create a new world when the brain pushes data to a register.
  takes a reg-name, a robot, a world, and data,
  and returns the running of the register's write function on the data and the world." 
  [{write :write} world data]
    (write world data)) 

(defn init-brain
  "initialize the brain, meaning all the internal state variables that go along
  with the robot program when it's running, except for the registers,
  which are queried from the world (or the robot -- haven't decided yet)."
  [program]
  {:acc 0
   :instr-ptr 0
   :call-stack []
   :program program})

(defn resolve-arg [{arg-val :val arg-type :type} registers labels world]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (read-register (registers arg-val) world)
    nil))

(defn step-brain
  "takes a `world` and a pathway to a brain in that world, called `brain-path`.
  
  Only the brain (the internal state of the robot)
  will be different when we pass it back, for all of the operations 
  except 'TO', which may also alter the external state of the robot, or the wider world.

  (returns the current state of the world untouched if the instruction pointer
  has gone beyond the end of the program. TODO: maybe have an error for that."

  [robot world]
  (let [{:keys [registers brain]} robot
        {:keys [program acc instr-ptr call-stack]} brain
        {:keys [instrs labels]} program]
    ;(println acc instr-ptr call-stack instrs labels program brain robot)
    (if (>= instr-ptr (count instrs))
      world
      (let [[{command :val} arg] ((:instrs program) instr-ptr)
            resolve #(resolve-arg % registers labels world)
            assoc-world-brain #(assoc-in world [:robots (:idx robot) :brain] (into brain %))]
        (case command
          "GOTO"             (assoc-world-brain {:instr-ptr (resolve arg)})
          "GOSUB"            (assoc-world-brain {:instr-ptr (resolve arg)
                                                 :call-stack (conj call-stack (inc instr-ptr))})
          "ENDSUB"           (assoc-world-brain {:instr-ptr (peek call-stack)
                                                 :call-stack (pop call-stack)})
          ("IF", ",")        (assoc-world-brain {:instr-ptr (inc instr-ptr)
                                                 :acc (resolve arg)})
          ("+" "-" "*" "/")  (assoc-world-brain {:instr-ptr (inc instr-ptr)
                                                 :acc ((op-map command) acc (resolve arg))})
          ("=" ">" "<" "#")  (if ((op-map command) acc (resolve arg))
                               (assoc-world-brain {:instr-ptr (inc instr-ptr)})
                               (assoc-world-brain {:instr-ptr (+ instr-ptr 2)}))
          "TO"               (write-register 
                               (registers (:val arg))
                               (assoc-world-brain {:instr-ptr (inc instr-ptr)})
                               acc))))))

