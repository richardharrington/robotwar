(ns hs-robotwar.core-test
  (:require [clojure.test :refer :all]
            [hs-robotwar.core :refer :all]))

(deftest digit?-negative
  (testing "not a digit"
    (is (not (digit? \f)))))

(deftest digit?-char-positive
  (testing "digit char"
    (is (digit? "6"))))

(deftest digit?-str-positive
  (testing "digit str"
    (is (digit? \6))))

(def line1 "IF DAMAGE # D GOTO MOVE")
(def line2 "AIM-17 TO AIM")
(def line3 "IF X<-5 GOTO SCAN")

(def tokens1 [{:token "IF", :pos 0} 
              {:token "DAMAGE", :pos 3} 
              {:token "#", :pos 10} 
              {:token "D", :pos 12} 
              {:token "GOTO", :pos 14} 
              {:token "MOVE", :pos 19}])

(def tokens2 [{:token "AIM", :pos 0} 
              {:token "-", :pos 3} 
              {:token "17", :pos 4} 
              {:token "TO", :pos 7} 
              {:token "AIM", :pos 10}])

(def tokens3 [{:token "IF", :pos 0} 
              {:token "X", :pos 3} 
              {:token "<", :pos 4} 
              {:token "-5", :pos 5} 
              {:token "GOTO", :pos 8} 
              {:token "SCAN", :pos 13}])

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
