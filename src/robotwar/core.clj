(ns robotwar.core)

(use '[clojure.set :only (union)])
(use '[clojure.string :only (split join)])

(def operators #{ "-" "+" "*" "/" "=" "#" "<" ">"})

(def operator-map {"-" -
                   "+" +
                   "*" *
                   "/" #(int (Math/round (float (/ %1 %2))))
                   "=" =
                   "<" <
                   ">" >
                   "#" not=})

(def registers-vec [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M"
                     "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                     "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" "DATA" ])

(def get-register-by-idx 
  "to allow use of the INDEX/DATA register pair, we need to have a way of 
  getting registers by their index, starting from 1."
  (zipmap (map inc (range (count registers-vec))) registers-vec))

(def registers (set registers-vec))

(def commands (union operators 
                     #{"TO" "IF" "GOTO" "GOSUB" "ENDSUB"}))

(defn re-seq-with-pos
  "Returns a lazy sequence of successive matches of pattern in string with position.
  Largely stolen from re-seq source."
  [^java.util.regex.Pattern re s]
  (let [m (re-matcher re s)]
    ((fn step []
       (when (.find m)
         (cons [(re-groups m) (.start m)] (lazy-seq (step))))))))

(defn strip-comments
  [line]
  (re-find #"[^;]*" line))

(def lex-re 
  (let [opstring (join operators)]
    (re-pattern (str "[" opstring "]|[^" opstring "\\s]+"))))

(defn lex-line
  [line]
  (map (fn [[s n]] {:token-str s, :pos n}) 
       (re-seq-with-pos lex-re line)))

(defn lex
  [src-code]
  (mapcat lex-line (split src-code #"\n")))

(defn str->int
  "Integer/parseInt, but returns nil on failure"
  [s-raw]
  (try (Integer/parseInt s-raw)
       (catch Exception e nil)))

(defn valid-word
   "Capital letters and numbers, starting with a capital letter"
  [s]
  (re-matches #"^[A-Z][A-Z\d]*" s))

(def return-err (constantly "Invalid word or symbol"))

(def parser-priority
 [[registers  :register]
  [commands   :command]
  [str->int   :number]
  [valid-word :label]
  [return-err :error]])

(defn parse-token
  [{:keys [token-str pos]}]
  (loop [[[parser token-type] & tail] parser-priority]
    (if-let [token-val (parser token-str)]
      {:val token-val, :type token-type, :pos pos}
      (recur tail))))

(defn parse
  "take the tokens and convert them to strucured source code ready for compiling"
  [tokens]
  (reduce (fn [parsed token]
            (if (= (:type (last parsed)) :error)
              parsed
              (conj parsed (parse-token token))))
          []
          tokens))

(defn disambiguate-minus-signs
  [initial-tokens]
  (loop [tokens initial-tokens
         results []]
    (let [{prev-type :type} (last results)
          {current-val :val, current-pos :pos :as current-token} (first tokens)
          {next-val :val, next-type :type :as next-token} (second tokens)]
      (cond
        (empty? tokens) results
        (and (not (#{:number :register} prev-type)) (= current-val "-") (= next-type :number))
          (recur (rest (rest tokens)) 
                 (conj results {:val (- next-val), :pos current-pos, :type :number})) 
        :otherwise (recur (rest tokens) (conj results current-token))))))

(defn rw-compile
  "Compiles the tokens into token-pairs. Commands consume the next token.
  Values form the special token-pair that is a comma followed by a value 
  (meaning push the value into the accumulator)"
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         result []]
    (if (empty? tokens)
      result
      (case (:type token)
        :command             (recur (rest tail) (conj result [token (first tail)]))
        (:number :register)  (recur tail (conj result [{:val ",", :type :command, :pos (:pos token)} token]))
        :label               (recur tail (conj result [token nil]))))))

(defn map-labels
  "Maps label-names to their appropriate indexes in the instruction list,
  and remove the labels from the instruction list itself (except as targets)"
  [initial-instrs]
  (loop [[instr & tail :as instrs] initial-instrs
         {label-map :labels instr-vec :instrs :as result} {:labels {}, :instrs []}
         idx 0]
    (if (empty? instrs)
      result
      (let [{command-type :type, command-val :val} (first instr)]
        (if (= command-type :label) 
          (recur tail 
                 {:labels (assoc label-map command-val idx), :instrs instr-vec} 
                 idx)
          (recur tail
                 {:labels label-map, :instrs (conj instr-vec instr)}
                 (inc idx)))))))

(defn pretty-print-tokens [instrs-tree]
  "This is hacky and just a temporary display function"
  (letfn [(f [[fst snd]]
            (if snd
              (format "%2d %9s %8s %20d %9s %11s"
                      (:pos fst) (:type fst) (:val fst) 
                      (:pos snd) (:type snd) (:val snd))
              (format "%2d %9s %8s"
                      (:pos fst) (:type fst) (:val fst))))]
    (str 
      "labels:\n" 
      (instrs-tree :labels)
      "\n\ninstructions:\n"
      (join "\n" (map f (instrs-tree :instrs))))))


(defn compile-to-obj-code [string]
  (-> string strip-comments lex parse disambiguate-minus-signs rw-compile map-labels))

(defn repl
  "make it so"
  []
  (loop [input (read-line)]
    (when (not= input "exit")
      (-> input
          strip-comments
          lex
          parse
          disambiguate-minus-signs
          rw-compile
          map-labels
          pretty-print-tokens
          println)
      (recur (read-line)))))



(def robot-source-code-test "WAIT GOTO WAIT")
(def robot-program-test (compile-to-obj-code robot-source-code-test))


(def robot-state-test {:acc nil
                       :instr-ptr 0
                       :call-stack []
                       :registers nil
                       :program robot-program-test})


(defn resolve-arg [{arg-val :val arg-type :type} registers labels]
  "resolves an instruction argument to a numeric value
  (either an arithmetic or logical comparison operand, or an instruction pointer)."
                  (case arg-type
                    :label (labels arg-val)
                    :number arg-val
                    :register (case arg-val
                                "RANDOM" (rand-int (registers arg-val))
                                "DATA" (registers (get-register-by-idx (registers "INDEX")))
                                (registers arg-val))
                    nil))

(def registers-with-effect-on-world #{"AIM" "SHOT" "RADAR"})
  
(defn tick-robot
  "takes as input a data structure representing all that the robot's brain
  needs to know about the world:

  1) The robot program, consisting of a vector of two-part instructions
     (a command, followed by an argument or nil) as well as a map of labels to 
     instruction numbers
  2) The instruction pointer (an index number for the instruction vector) 
  3) The value of the accumulator, or nil
  4) The call stack (a vector of instruction pointers to lines following
     GOSUB calls; this will not get out of hand because no recursion,
     mutual or otherwise, will be allowed. TODO: implement this restriction)
  5) The contents of all the registers
  
  After executing one instruction, tick-robot returns the updated verion of all of the above, 
  plus an optional :action field, to notify the world if the AIM, SHOT, or RADAR registers have
  been pushed to."

  [{:keys [acc instr-ptr call-stack registers], {:keys [labels instrs]} :program :as state}]
  (let [[{command :val} {unresolved-arg :val :as arg}] (instrs instr-ptr)
        inc-instr-ptr #(assoc % instr-ptr (inc instr-ptr))
        skip-next-instr-ptr #(assoc % instr-ptr (+ instr-ptr 2))
        resolve #(resolve-arg % registers labels)]
    (case command
      "GOTO"             (assoc state instr-ptr (resolve arg))
      "GOSUB"            (assoc (assoc state call-stack (conj call-stack (inc instr-ptr)))
                                instr-ptr 
                                (resolve arg))
      "ENDSUB"           (assoc (assoc state call-stack (pop call-stack))
                                instr-ptr
                                (peek call-stack))
      ("IF" ",")         (inc-instr-ptr (assoc state acc (resolve arg)))
      ("+" "-" "*" "/")  (inc-instr-ptr (assoc state acc (operator-map acc (resolve arg))))
      ("=" ">" "<" "#")  (if (operator-map acc (resolve arg))
                           (inc-instr-ptr state)
                           (skip-next-instr-ptr state))
      "TO"               (let [return-state (inc-instr-ptr (assoc-in state [registers unresolved-arg] acc))]
                           (if (registers-with-effect-on-world unresolved-arg)
                             (conj return-state {:action unresolved-arg})
                             return-state)))))



;
;
;
;(def starter-world {:width 250 :height 250})
;
;(defn play
;  "takes a vector of robots, and plays a game"
;  [robots {width :width, height :height :as world}]
;  ()
;
;
;(fn [l]
;  (let sub-seq [(reduce (fn [acc val]
;                          )
;                        [(first l)]
;                        l)]
;    (when (> 1 (count sub-seq))
;      sub-seq)))
;
