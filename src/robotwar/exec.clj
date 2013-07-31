(ns robotwar.exec
  (:require (robotwar [lexicon :as lexicon]))
  (:use [clojure.string :only [join]]))

(def op-map (zipmap lexicon/op-commands 
                    (map (fn [op] 
                           (case op
                             "/" #(int (Math/round (float (/ %1 %2))))
                             "#" not=
                             (-> op read-string eval)))
                         lexicon/op-commands)))

(defn resolve-register [registers reg]
  (case reg
    "RANDOM" (rand-int (registers reg))
    "DATA" (registers (lexicon/registers (registers "INDEX")))
    (registers reg)))

(defn resolve-arg [{arg-val :val arg-type :type} registers labels]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
  (case arg-type
    :label     (labels arg-val)
    :number    arg-val
    :register  (resolve-register registers arg-val)
    nil))

(def registers-with-effect-on-world #{"SHOT" "RADAR" "SPEEDX" "SPEEDY"})

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

  [{:keys [acc instr-ptr call-stack registers program] :as state}]
  (let [[{command :val} {unresolved-arg-val :val :as arg}] ((program :instrs) instr-ptr)
        resolve #(resolve-arg % registers (program :labels))]
    (case command
      "GOTO"             (into state {:instr-ptr (resolve arg)})
      "GOSUB"            (into state {:instr-ptr (resolve arg)
                                      :call-stack (conj call-stack (inc instr-ptr))})
      "ENDSUB"           (into state {:instr-ptr (peek call-stack)
                                      :call-stack (pop call-stack)})
      ("IF", ",")        (into state {:instr-ptr (inc instr-ptr)
                                      :acc (resolve arg)})
      ("+" "-" "*" "/")  (into state {:instr-ptr (inc instr-ptr)
                                      :acc ((op-map command) acc (resolve arg))})
      ("=" ">" "<" "#")  (if ((op-map command) acc (resolve arg))
                           (into state {:instr-ptr (inc instr-ptr)})
                           (into state {:instr-ptr (+ instr-ptr 2)}))
      "TO"               (let [return-state (into state {:instr-ptr (inc instr-ptr)
                                                         :registers (into registers {unresolved-arg-val acc})})]
                           (if (registers-with-effect-on-world unresolved-arg-val)
                             (conj return-state {:action unresolved-arg-val})
                             return-state)))))

(defn init-robot-state
  "initialize all the state variables that go along
  with the robot program when it's running." 
  [program register-vals]
  {:program program
   :acc 0
   :instr-ptr 0
   :registers (into (zipmap lexicon/registers (repeat 0))
                    register-vals)
   :call-stack []})

(defn init-world
  "initialize all the variables for a robot world"
  [width height programs]
  {:width width
   :height height
   :shells []
   :robots (map-indexed (fn [idx program]
                          {:internal-state (init-robot-state program 
                                                             {"X" (rand-int width)
                                                              "Y" (rand-int height)}) 
                           :external-state {:icon (str idx)}}) 
                        programs)})

(defn tick-world
  "TODO"
  [world-state])

(defn arena-text-grid
  "outputs the arena, with borders"
  [{:keys [width height robots]}]
  (let [horiz-border-char "-"
        vert-border-char "+"
        header-footer (apply str (repeat (+ width 2) horiz-border-char))
        field (for [y (range height), x (range width)]
                (some (fn [{{{robot-x "X" robot-y "Y"} :registers} :internal-state
                            {icon :icon} :external-state}]
                        (if (= [x y] [robot-x robot-y])
                          icon
                          " "))
                      robots))]
    (str header-footer
         "\n" 
         (join "\n" (map #(join (apply str %) (repeat 2 vert-border-char))
                         (partition width field))) 
         "\n" 
         header-footer)))