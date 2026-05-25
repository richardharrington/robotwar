(ns robotwar.handler-test
  (:require [clojure.test :refer :all]
            [robotwar.handler :refer :all]))

(deftest app-handler-test
  (testing "program-names"
    (let [response (app {:request-method :get :uri "/program-names"})]
      (is (= (:status response) 200))
      (is (.contains (:body response) "mover"))))

  (testing "not-found route"
    (let [response (app {:request-method :get :uri "/invalid"})]
      (is (nil? response))))

  (testing "unsupported http request method"
    (let [response (app {:request-method :put :uri "/program-names"})]
      (is (nil? response))))

  (testing "files"
    (let [response (app {:request-method :get :uri "/index.html"})]
      (is (= (:status response) 200))
      (is (.contains (slurp (:body response)) "Welcome to the future")))
    (let [response (app {:request-method :get :uri "/js/cljs-runtime/cljs-app.js"})]
      (is (= (:status response) 200))))

  (testing "worlds route"
    (let [response (app {:request-method :get :uri "/worlds/0/99"})]
      (is (= (:status response) 200))
      (is (.contains (:body response) "["))))

  (testing "init route"
    (let [response (app {:request-method :get :uri "/init"
                         :query-string "programs=mover"})]
      (is (= (:status response) 200))
      (is (.contains (:body response) "game-info")))))
