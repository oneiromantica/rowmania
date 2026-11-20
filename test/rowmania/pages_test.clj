(ns rowmania.pages-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [rowmania.pages :as pages]))

(deftest login-page-escapes-input
  (let [html (pages/login-page {:error "<b>x</b>" :name "\"><script>"})]
    (is (not (str/includes? html "<script>")))
    (is (not (str/includes? html "<b>x</b>")))))

(deftest empty-states
  (is (str/includes? (pages/challenge-page {:summary nil}) "keiner Challenge"))
  (is (str/includes? (pages/sessions-page {:selected nil}) "keiner Challenge")))
