(ns rowmania.challenge-view-test
  (:require [clojure.test :refer [deftest is]]
            [rowmania.challenge-view :as cv]
            [rowmania.values :as v]))

(defn- date [s] (:ok (v/parse-iso-date s)))

(def challenge {:challenge/id (random-uuid) :challenge/name "Herbst"
                :challenge/start (date "2026-10-01") :challenge/end (date "2026-12-31")
                :challenge/target-distance 600000})
(def ada {:user/id (random-uuid) :user/name "Ada"})
(def bob {:user/id (random-uuid) :user/name "Bob"})

(defn- session [user day metres]
  {:session/date (date day) :session/distance metres :session/user user})

(deftest summarizes-totals-weeks-and-shares
  (let [summary (cv/summarize {:challenge challenge :participants [ada bob] :me-id (:user/id ada)
                               :today (date "2026-10-12")
                               :sessions [(session ada "2026-10-02" 10000)
                                          (session ada "2026-10-09" 5000)
                                          (session bob "2026-10-10" 5000)
                                          (session bob "2026-09-30" 99999)]}) ; außerhalb
        [first-member second-member] (:members summary)]
    (is (= 20000 (:total summary)))
    (is (= 2 (:weeks summary)))
    (is (= "Ada" (:name first-member)))
    (is (= [10000 15000] (:weekly first-member)))
    (is (= [0 5000] (:weekly second-member)))
    (is (= [75 25] (map :share (:members summary))))
    (is (:me? first-member))))

(deftest chooses-requested-or-running-challenge
  (let [old (assoc challenge :challenge/id (random-uuid)
                   :challenge/start (date "2025-01-01") :challenge/end (date "2025-02-01"))]
    (is (= old (cv/choose-challenge [challenge old] (date "2026-11-01") (:challenge/id old))))
    (is (= challenge (cv/choose-challenge [old challenge] (date "2026-11-01") nil)))
    (is (nil? (cv/choose-challenge [] (date "2026-11-01") nil)))))
