(ns hs-robotwar.core-test
  (:require [clojure.test :refer :all]
            [hs-robotwar.core :refer :all]))

(def line1 "IF DAMAGE # D GOTO MOVE")
(def line2 "AIM-17 TO AIM")
(def line3 "IF X<-5 GOTO SCAN")

(def tokens1 [{:token-str "IF", :pos 0} 
              {:token-str "DAMAGE", :pos 3} 
              {:token-str "#", :pos 10} 
              {:token-str "D", :pos 12} 
              {:token-str "GOTO", :pos 14} 
              {:token-str "MOVE", :pos 19}])

(def tokens2 [{:token-str "AIM", :pos 0} 
              {:token-str "-", :pos 3} 
              {:token-str "17", :pos 4} 
              {:token-str "TO", :pos 7} 
              {:token-str "AIM", :pos 10}])

(def tokens3 [{:token-str "IF", :pos 0} 
              {:token-str "X", :pos 3} 
              {:token-str "<", :pos 4} 
              {:token-str "-", :pos 5} 
              {:token-str "5", :pos 6} 
              {:token-str "GOTO", :pos 8} 
              {:token-str "SCAN", :pos 13}])

(deftest lex-simple
  (testing "lexing of simple line"
    (is (= (lex-line line1) 
           tokens1))))

(deftest lex-scrunched-chars
  (testing "lexing with no whitespace between operators and operands"
    (is (= (lex-line line2)
           tokens2)))) 

(deftest lex-negative-numbers
  (testing "lexing with unary negative operator"
    (is (= (lex-line line3)
           tokens3))))

(deftest lex-multi-line
  (testing "lexing multiple lines"
    (is (= (lex (clojure.string/join "\n" [line1 line2 line3]))
           (concat tokens1 tokens2 tokens3)))))

(deftest str->int-fail
  (testing "failure of str->int"
    (is (= (str->int "G")
           nil))))

(deftest str->int-success-positive-number
  (testing "str->int with positive number"
    (is (= (str->int "8")
           8))))

(deftest str->int-success-negative-number
  (testing "str->int with negative number"
    (is (= (str->int "-9")
           -9))))

(deftest valid-word-fail-because-lower-case
  (testing "word not valid because lower case"
    (is (= (valid-word "Beatles")
           nil))))

(deftest valid-word-fail-because-starts-with-number
  (testing "word not valid because starts with number"
    (is (= (valid-word "7BEATLES")))))

(deftest valid-word-success
  (testing "valid word"
    (is (= (valid-word "BEATLES7")
           "BEATLES7"))))

(deftest parse-token-register
  (testing "parsing register token"
    (is (= (parse-token {:token-str "AIM", :pos 0})
           {:val "AIM", :type :register, :pos 0}))))

(deftest parse-token-command-word
  (testing "parsing command token (word)"
    (is (= (parse-token {:token-str "GOTO", :pos 14})
           {:val "GOTO", :type :command, :pos 14}))))

(deftest parse-token-command-operator
  (testing "parsing command token (operator)"
    (is (= (parse-token {:token-str "#", :pos 10})
           {:val "#", :type :command, :pos 10}))))

(deftest parse-token-number
  (testing "parsing number token"
    (is (= (parse-token {:token-str "-17", :pos 4}))
        {:val -17, :type :number, :pos 4})))

(deftest parse-token-label
  (testing "parsing label token"
    (is (= (parse-token  {:token-str "SCAN", :pos 13})
           {:val "SCAN", :type :label, :pos 13}))))

(deftest parse-token-error
  (testing "parsing error token"
    (is (= (parse-token {:token-str "-GOTO", :pos 23})
           {:val "Invalid word or symbol", :type :error, :pos 23}))))
          

 
