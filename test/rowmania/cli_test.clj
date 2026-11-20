(ns rowmania.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datahike.api :as d]
            [rowmania.cli :as cli]
            [rowmania.cli.parse :as parse]
            [rowmania.test-db :refer [fresh-conn]]))

(defn- run
  "Führt argv gegen eine frische oder gegebene Verbindung aus."
  [conn argv]
  (let [database {:db-value (fn [] @conn) :transact! #(d/transact conn %)}
        deps     {:new-id random-uuid :hash-password #(str "hash:" %)}]
    (let [parsed (parse/parse-args argv)]
      (if (:error parsed) parsed (cli/run-command database deps (:ok parsed))))))

(deftest parse-errors
  (is (:error (parse/parse-args [])))
  (is (:error (parse/parse-args ["--nope"])))
  (is (:error (parse/parse-args ["--add-user" "ada"])))
  (is (:error (parse/parse-args ["--create-challenge" "--name" "x"])))
  (is (:error (parse/parse-args ["--create-challenge" "--name" "x" "--start" "kaputt" "--end" "2026-01-02" "--goal" "5"])))
  (is (:error (parse/parse-args ["--add-session" "c" "u" "--date" "2026-01-01" "--distance" "-4"]))))

(defspec uuid-references-parse-as-id 50
  (prop/for-all [id (gen/fmap (fn [_] (random-uuid)) gen/nat)]
    (= {:ok {:id id}} (parse/parse-reference (str id)))))

(deftest user-lifecycle
  (let [conn (fresh-conn)]
    (is (:ok (run conn ["--add-user" "ada" "geheim"])))
    (testing "Name ist eindeutig"
      (is (:error (run conn ["--add-user" "ada" "anders"]))))
    (is (:ok (run conn ["--update-user-name" "ada" "grace"])))
    (is (:ok (run conn ["--update-user-pass" "grace" "neu"])))
    (is (= "hash:neu" (d/q '[:find ?h . :where [?u :user/name "grace"] [?u :user/password-hash ?h]] @conn)))
    (is (= 1 (count (:ok (run conn ["--list-users"])))))
    (is (re-matches #"grace [0-9a-f-]{36}" (first (:ok (run conn ["--list-users"])))))
    (is (:error (run conn ["--update-user-name" "unbekannt" "x"])))))

(deftest challenge-and-session
  (let [conn (fresh-conn)]
    (run conn ["--add-user" "ada" "pw"])
    (is (:ok (run conn ["--create-challenge" "--name" "Winter" "--start" "2026-01-01" "--end" "2026-01-31" "--goal" "100000"])))
    (is (:error (run conn ["--create-challenge" "--name" "Winter" "--start" "2026-02-01" "--end" "2026-02-28" "--goal" "5"])))
    (is (:error (run conn ["--create-challenge" "--name" "Rueckwaerts" "--start" "2026-03-01" "--end" "2026-02-01" "--goal" "5"])))
    (is (:ok (run conn ["--update-challenge-goal" "Winter" "120000"])))
    (is (:error (run conn ["--update-challenge-start" "Winter" "2026-02-15"])))
    (is (:ok (run conn ["--update-challenge-name" "Winter" "Winter26"])))
    (is (:ok (run conn ["--add-session" "Winter26" "ada" "--date" "2026-01-05" "--distance" "5000"])))
    (is (= 120000 (d/q '[:find ?g . :where [?c :challenge/name "Winter26"] [?c :challenge/target-distance ?g]] @conn)))
    (is (= #{"ada"} (set (d/q '[:find [?n ...] :where [?c :challenge/participants ?u] [?u :user/name ?n]] @conn))))
    (is (:error (run conn ["--add-session" "Gibtsnicht" "ada" "--date" "2026-01-05" "--distance" "1"])))))
