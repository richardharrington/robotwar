(ns robotwar.brain
  (:use [clojure.pprint :only [pprint]])
  (:require [robotwar.assembler :as assembler]))

(def op-map (into {} (for [op assembler/op-commands]
                       [op (case op
                             "/" #(int (Math/round (float (/ %1 %2))))
                             "#" not=
                             (-> op read-string eval))])))

(defn init-brain
  "initialize the brain, meaning all the internal state variables that go along
  with the robot program when it's running."
  [src-code registers]
  {:acc 0
   :instr-ptr 0
   :call-stack []
   :registers registers
   :obj-code (assembler/assemble src-code)})

(defn resolve-arg [{arg-val :val arg-type :type} registers labels world read-register]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (read-register (registers arg-val) world)
    nil))

(defn tick-brain
  "takes a robot and a world. returns a world.

  Only the brain (the internal state of the robot)
  will be different in the new world we pass back, for all of the operations 
  except 'TO', which may also alter the external state of the robot, or the wider world.

  (returns the current state of the world untouched if the instruction pointer
  has gone beyond the end of the program. TODO: maybe have an error for that."

  [{brain :brain :as robot} world read-register write-register]
  (let [{:keys [obj-code acc instr-ptr call-stack registers]} brain
        {:keys [instrs labels]} obj-code]
    (if (>= instr-ptr (count instrs))
      world
      (let [[{command :val} arg] ((:instrs obj-code) instr-ptr)
            resolve #(resolve-arg % registers labels world read-register)
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

