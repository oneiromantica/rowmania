(ns rowmania.test-db
  (:require [datahike.api :as d]
            [rowmania.db-schema :refer [schema]]))

(defn fresh-conn
  "Jede Verbindung bekommt einen eigenen In-Memory-Store."
  []
  (let [config {:store {:backend :memory :id (random-uuid)} :schema-flexibility :write}]
    (d/create-database config)
    (let [conn (d/connect config)]
      (d/transact conn schema)
      conn)))
