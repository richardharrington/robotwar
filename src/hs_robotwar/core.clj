(ns hs-robotwar.core)



(def error-type-map
  {:parse-error "Parse Error"
   :end-of-line-error "End of Line Error"})

(defn error 
  [error-type error-string]
  (str (error-type-map error-type) ": " error-string))

(defn valid-number-string
  [n]
  (re-find #"^-?\d+\.?\d*$" n))

(defn number-or-string
  [x]
  (if (valid-number-string x)
    (Float/parseFloat x)
    x))
  
(defn parse
  "parses a line, by returning a map with the expression, and meta-
  information. Right now the value is just a number if it's supposed
  to be a number, otherwise the original string."
  [line]
  (reduce (fn [ast ]))
  (map (fn [word]
         {:column-number }
         (number-or-string word))
       (re-seq #"\S+" line)))

(defn validate-expr
  "right now just checks to see whether it's a number.
   will do a lot more things in the future."
  [expr]
  (if (number? expr)
    [expr nil]
    [expr (error :parse-error "Not a number")]))
  

(defn evaluate
  "evaluates an ast (right now just prints it).
  Checks to see if it's a string because that's
  the way errors are passed. We'll change that."
  [ast]
  (if (string? ast)
    ast
    (apply str (interpose " " ast))))
  

(defn repl
  "make it so"
  []
  (loop []
    (println (evaluate (parse (lex (read-line)))))
    (recur)))
