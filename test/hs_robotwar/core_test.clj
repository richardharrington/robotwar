(ns hs-robotwar.core-test
  (:require [clojure.test :refer :all]
            [hs-robotwar.core :refer :all]))

(def line1 "IF DAMAGE # D GOTO MOVE    ; comment or something")

(def line-no-comments1 "IF DAMAGE # D GOTO MOVE")
(def line-no-comments2 "AIM-17 TO AIM")
(def line-no-comments3 "IF X<-5 GOTO SCAN")

(def lexed-tokens1 [{:token-str "IF", :pos 0} 
                    {:token-str "DAMAGE", :pos 3} 
                    {:token-str "#", :pos 10} 
                    {:token-str "D", :pos 12} 
                    {:token-str "GOTO", :pos 14} 
                    {:token-str "MOVE", :pos 19}])

(def lexed-tokens2 [{:token-str "AIM", :pos 0} 
                    {:token-str "-", :pos 3} 
                    {:token-str "17", :pos 4} 
                    {:token-str "TO", :pos 7} 
                    {:token-str "AIM", :pos 10}])

(def lexed-tokens3 [{:token-str "IF", :pos 0} 
                    {:token-str "X", :pos 3} 
                    {:token-str "<", :pos 4} 
                    {:token-str "-", :pos 5} 
                    {:token-str "5", :pos 6} 
                    {:token-str "GOTO", :pos 8} 
                    {:token-str "SCAN", :pos 13}])

(def lexed-tokens4 [{:token-str "AIM", :pos 0} 
                    {:token-str "@", :pos 3} 
                    {:token-str "17", :pos 4} 
                    {:token-str "TO", :pos 7} 
                    {:token-str "AIM", :pos 10}])

(def parsed-tokens2 [{:val "AIM", :type :register, :pos 0} 
                     {:val "-", :type :command, :pos 3} 
                     {:val 17, :type :number, :pos 4} 
                     {:val "TO", :type :command, :pos 7} 
                     {:val "AIM", :type :register, :pos 10}])

(def parsed-tokens3 [{:val "IF", :type :command, :pos 0} 
                     {:val "X", :type :register, :pos 3} 
                     {:val "<", :type :command, :pos 4} 
                     {:val "-", :type :command, :pos 5} 
                     {:val 5, :type :number, :pos 6} 
                     {:val "GOTO", :type :command, :pos 8} 
                     {:val "SCAN", :type :label, :pos 13}])

(def parsed-tokens4 [{:val "AIM", :type :register, :pos 0} 
                     {:val "Invalid word or symbol", :type :error, :pos 3}])

(def minus-sign-disambiguated-tokens3 [{:val "IF", :type :command, :pos 0} 
                                       {:val "X", :type :register, :pos 3} 
                                       {:val "<", :type :command, :pos 4} 
                                       {:val -5, :type :number, :pos 5} 
                                       {:val "GOTO", :type :command, :pos 8} 
                                       {:val "SCAN", :type :label, :pos 13}])

(deftest strip-comments-test
  (testing "stripping comments"
    (is (= (strip-comments line1)
           "IF DAMAGE # D GOTO MOVE    "))))

(deftest lex-simple
  (testing "lexing of simple line"
    (is (= (lex-line line-no-comments1) 
           lexed-tokens1))))

(deftest lex-scrunched-chars
  (testing "lexing with no whitespace between operators and operands"
    (is (= (lex-line line-no-comments2)
           lexed-tokens2)))) 

(deftest lex-negative-numbers
  (testing "lexing with unary negative operator"
    (is (= (lex-line line-no-comments3)
           lexed-tokens3))))

(deftest lex-multi-line
  (testing "lexing multiple lines"
    (is (= (lex (clojure.string/join "\n" [line-no-comments1 
                                           line-no-comments2 
                                           line-no-comments3]))
           (concat lexed-tokens1 lexed-tokens2 lexed-tokens3)))))

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
          
(deftest parse-tokens-minus-sign
  (testing "parsing tokens with a binary minus sign"
    (is (= (parse lexed-tokens2)
           parsed-tokens2))))

(deftest parse-tokens-negative-sign
  (testing "parsing tokens with a unary negative sign"
    (is (= (parse lexed-tokens3)
           parsed-tokens3))))

(deftest parse-tokens-error
  (testing "parsing tokens with an invalid operator"
    (is (= (parse lexed-tokens4)
           parsed-tokens4))))

(def minus-sign-disambiguated-tokens2 parsed-tokens2)

(def minus-sign-disambiguated-tokens3 [{:val "IF", :type :command, :pos 0} 
                                       {:val "X", :type :register, :pos 3} 
                                       {:val "<", :type :command, :pos 4} 
                                       {:val -5, :type :number, :pos 5} 
                                       {:val "GOTO", :type :command, :pos 8} 
                                       {:val "SCAN", :type :label, :pos 13}])

(deftest disambiguate-minus-signs-binary
  (testing "disambiguating minus signs, result should be subtraction operator"
    (is (= (disambiguate-minus-signs parsed-tokens2)
           parsed-tokens2))))

(deftest disambiguate-minus-signs-unary
  (testing "disambiguating minus signs, result should be unary negative sign"
    (is (= (disambiguate-minus-signs parsed-tokens3)
           minus-sign-disambiguated-tokens3))))
