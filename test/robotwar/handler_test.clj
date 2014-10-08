(ns robotwar.handler-test
  (:require [clojure.test :refer :all]
            [robotwar.handler :refer :all]
            [ring.mock.request :as mock]))
  
  
(deftest app-handler-test
  (testing "program-names"
    (let [response (app (mock/request :get "/program-names"))]
      (is (= (:status response) 200))
      (is (.contains (:body response) "mover"))))
  
  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404))))
  
  (testing "files"
    (let [response (app (mock/request :get "/index.html"))]
      (is (= (:status response) 200))
      (is (.contains (slurp (:body response)) "Welcome to the future")))
    (let [response (app (mock/request :get "/js/main.js"))]
      (is (= (:status response) 200))
      (is (.contains (slurp (:body response)) "function"))))
  
  (testing "worlds route"
    (let [response (app (mock/request :get "/worlds/0/99"))]
      (is (= (:status response) 200))
      (is (.contains (:body response) "["))))
  
  (testing "init route"
    (let [response (app (mock/request :get "/init?programs=mover"))]
      (is (= (:status response) 200))
      (is (.contains (:body response) "game-info")))))