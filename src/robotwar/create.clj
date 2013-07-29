(ns robotwar.create
  (:refer-clojure :exclude [compile])
  (:use (clojure [string :only [split join]] 
                 [pprint :only [pprint]])
        [robotwar.lexicon :only [registers op-commands commands]]))

(defn re-seq-with-pos
  "Returns a lazy sequence of successive matches of pattern in string with position.
  Largely stolen from re-seq source."
  [^java.util.regex.Pattern re s]
  (let [m (re-matcher re s)]
    ((fn step []
       (when (.find m)
         (cons [(re-groups m) (.start m)] (lazy-seq (step))))))))

(defn split-lines
  [raw-lines]
  (split raw-lines #"\n"))

(defn strip-comments
  [lines]
  (map #(re-find #"[^;]*" %) lines))

(def lex-re 
  (let [op-string (join op-commands)]
    (re-pattern (str "[" op-string "]|[^" op-string "\\s]+"))))

(defn lex-line
  "Helper function for lex. Note: :line and :pos
  are intended to be human-readable for error-reporting
  purposes, so they're indexed from 1."
  [line-num line]
  (map (fn [[s n]] 
         {:token-str s, :line (inc line-num), :pos (inc n)}) 
       (re-seq-with-pos lex-re line)))

(defn lex
  "Lexes a sequence of lines. After this point, line numbers
  are captured as metadata and tokens are no longer grouped by line."
  [lines]
  (apply concat (map-indexed lex-line lines)))

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
 [[(set registers)  :register]
  [(set commands)   :command]
  [str->int         :number]
  [valid-word       :label]
  [return-err       :error]])

(defn parse-token
  [{:keys [token-str pos line]}]
  (loop [[[parser token-type] & tail] parser-priority]
    (if-let [token-val (parser token-str)]
      {:val token-val, :type token-type, :pos pos :line line}
      (recur tail))))

(defn parse
  "take the tokens and convert them to structured source code ready for compiling.
  if there's an error, returns a different type: just the token,
  outside of any sequence."
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         parsed-tokens []]
    (if (empty? tokens)
      parsed-tokens
      (let [parsed-token (parse-token token)]
        (if (= (:type parsed-token) :error)
          parsed-token
          (recur tail (conj parsed-tokens parsed-token)))))))

(defn disambiguate-minus-signs
  [initial-tokens]
  (loop [tokens initial-tokens
         results []]
    (let [{prev-type :type} (last results)
          [{current-val :val :as current-token} 
           & [{next-val :val, next-type :type :as next-token} :as tail]] tokens]
      (cond
        (empty? tokens) results
        (and (#{"-"} current-val) 
             (#{:number} next-type) 
             (not (#{:number :register} prev-type)))
          (recur (rest tail) 
                 (conj results (into current-token {:val (- next-val), :type :number}))) 
        :otherwise (recur tail (conj results current-token))))))

(defn make-instr-pairs
  "Compiles the tokens into token-pairs. Commands consume the next token.
  Values form the special token-pair that is a comma followed by a value 
  (meaning push the value into the accumulator). The comma command re-uses
  the same :line and :pos metadata from the argument that follows it."
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         result []]
    (if (empty? tokens)
      result
      (case (:type token)
        :command             (recur (rest tail) (conj result [token (first tail)]))
        (:number :register)  (recur tail (conj result [(into token {:val ",", :type :command}) token]))
        :label               (recur tail (conj result [token nil]))))))

(defn map-labels
  "Maps label-names to their appropriate indexes in the instruction list,
  and remove the labels from the instruction list itself (except as targets)"
  [initial-instrs]
  (loop [[instr & tail :as instrs] initial-instrs
         result {:labels {} 
                 :instrs []}]
    (if (empty? instrs)
      result
      (let [command (first instr)
            next-instr-num (count (result :instrs))]
        (if (#{(command :type)} :label) 
          (recur tail (assoc-in result [:labels (command :val)] next-instr-num))
          (recur tail (assoc-in result [:instrs next-instr-num] instr)))))))

(defn compile [string]
  "compiles robotwar code, with error-checking beginning after the lexing
  step. All functions that return errors will return a map with the keyword 
  :error, and then a token with a :val field containing the error string, 
  and :pos and :line fields containing the location. So far only parse
  implements error-checking."
  (let [lexed (-> string split-lines strip-comments lex)]
   (reduce (fn [result step]
             (if (= (:type result) :error)
               result
               (step result)))
           lexed
           [parse disambiguate-minus-signs make-instr-pairs map-labels])))

(defn repl
  "make it so"
  []
  (loop [input (read-line)]
    (when (not= input "exit")
      (-> input compile pprint)
      (recur (read-line)))))

