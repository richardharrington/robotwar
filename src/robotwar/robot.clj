(ns robotwar.robot
  (:use [clojure.string :only [join]]
        [clojure.pprint :only [pprint]]
        (robotwar kernel-lexicon game-lexicon)))

; TODO: remove the game-lexicon dependency above, when it's no longer needed
; (i.e. when we've moved the resolve-register logic out of this module)

(def op-map (zipmap op-commands 
                    (map (fn [op] 
                           (case op
                             "/" #(int (Math/round (float (/ %1 %2))))
                             "#" not=
                             (-> op read-string eval)))
                         op-commands)))

(defn init-internal-state
  "initialize all the internal state variables that go along
  with the robot program when it's running.
  (Optionally, also pass in a hash-map of register names and values,
  which will override the defaults)." 
  [program reg-names & [registers]]
  (pprint program)
  (pprint reg-names)
  (pprint registers)
  (let [identity-with-throwaway-args (fn [x & args] x)]
    {:registers (into {} (concat
                           (for [reg-name reg-names]
                             ; default values for :read, :write and :data. 
                             ; (TODO: move these into a higher-level module)
                             ; NOTE: the default version of the :read function 
                             ; does not need the world-state parameter.
                             [reg-name {:read identity-with-throwaway-args
                                        :write (fn [robot data]
                                                 (assoc-in 
                                                   robot 
                                                   [:internal-state :registers reg-name] 
                                                   data))
                                        :data 0}])
                           registers))
     :program program
     :acc 0
     :instr-ptr 0
     :call-stack []}))

(defn read-register
  "returns a numeric value"
  [{:keys [read data] :as register} world]
  (read data world))

(defn write-register
  "returns a full robot state, including its external state and its internal brain state.
  TODO: implement extra flag to indicate if we've fired a shot."
  [robot {write :write :as register} data]
  (write robot data))

;(defn resolve-register [registers reg]
;  (case reg
;    "RANDOM" (rand-int (registers reg))
;    "DATA" (registers (reg-names (registers "INDEX")))
;    (registers reg)))

(defn resolve-arg [{arg-val :val arg-type :type} registers labels world]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer).
  If it's reading from a register, uses that register's :read function."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (read-register (registers arg-val) world)
    nil))

(defn tick-robot
  "takes as input a data structure representing all that the robot's brain
  needs to know about the world:

  1) The robot program, consisting of a vector of two-part instructions
  (a command, followed by an argument or nil) as well as a map of labels to 
  instruction numbers
  2) The instruction pointer (an index number for the instruction vector) 
  3) The value of the accumulator, or nil
  4) The call stack (a vector of instruction pointers to lines following
  GOSUB calls)
  5) The contents of all the registers

  After executing one instruction, tick-robot returns the updated verion of all of the above, 
  plus an optional :action field, to notify the world if the SHOT, SPEEDX, SPEEDY or RADAR 
  registers have been pushed to."

  [robot world]
  (let [internal-state (:internal-state robot)
        {:keys [acc instr-ptr call-stack registers program]} internal-state
        [{command :val} arg] ((program :instrs) instr-ptr)
        resolve #(resolve-arg % registers (program :labels) world)
        return-robot #(assoc robot :internal-state (into internal-state %))]
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

