(ns rowmania.test-runner
  "Startet alle Tests: `clojure -X:test`."
  (:require [clojure.test :as t]))

(def test-namespaces '[rowmania.pages-test rowmania.config-test rowmania.db-schema-test rowmania.cli-test rowmania.values-test rowmania.challenge-view-test rowmania.web-test])

(defn run [_]
  (apply require test-namespaces)
  (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
