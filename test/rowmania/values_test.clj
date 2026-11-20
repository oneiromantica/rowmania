(ns rowmania.values-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [rowmania.values :as v]))

(deftest kilometres
  (is (= {:ok 6500} (v/parse-kilometres "6,5")))
  (is (= {:ok 6500} (v/parse-kilometres "6.5")))
  (is (:error (v/parse-kilometres "0")))
  (is (:error (v/parse-kilometres "abc")))
  (is (:error (v/parse-kilometres "5000")))
  (is (= "6,5" (v/kilometres-text 6500)))
  (is (= "10" (v/kilometres-text 10000))))

(defspec kilometres-round-trip 100
  (prop/for-all [metres (gen/choose 100 1000000)]
    ;; Anzeige rundet auf 100 m; das Einlesen der Eingabe-Darstellung ist darauf stabil
    (let [shown (v/kilometres-input metres)]
      (= (:ok (v/parse-kilometres shown))
         (:ok (v/parse-kilometres (v/kilometres-input (:ok (v/parse-kilometres shown)))))))))

(deftest dates
  (let [date (:ok (v/parse-iso-date "2026-11-19"))]
    (is (= "Do, 19. November" (v/format-day date)))
    (is (= "2026-11-19" (v/iso-date date))))
  (is (:error (v/parse-iso-date "19.11.2026")))
  (is (:error (v/parse-iso-date nil)))
  (is (= "1. Oktober bis 31. Dezember 2026"
         (v/format-period (:ok (v/parse-iso-date "2026-10-01")) (:ok (v/parse-iso-date "2026-12-31"))))))
