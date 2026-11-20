(ns rowmania.cli
  "Kommandozeile zur Verwaltung von Nutzern, Challenges und Sessions."
  (:require [rowmania.cli.parse :as parse]
            [rowmania.cli.plan :as plan]
            [rowmania.config :as config]
            [rowmania.db :as db]
            [rowmania.passwords :refer [hash-password]]
            [rowmania.result :as r])
  (:gen-class))

(defn run-command
  "Führt einen geparsten Befehl gegen die DB aus.
  Gibt {:ok [zeilen]} oder {:error text} zurück."
  [{:keys [db-value transact!]} deps command]
  (r/bind (plan/plan-command (db-value) command deps)
          (fn [{:keys [tx report]}]
            (when (seq tx) (transact! tx))
            (r/ok report))))

(defn exit-code [result] (if (r/failed? result) 1 0))

(defn -main [& argv]
  (let [parsed (parse/parse-args argv)
        result (r/bind parsed
                       (fn [command]
                         ;; Die Verbindung wird nicht freigegeben: Datahike loggt das auf
                         ;; stdout und würde die Ausgabe (z. B. --list-users) verunreinigen.
                         (run-command (db/init-db (:db (config/read-settings "config.edn")))
                                      {:new-id random-uuid :hash-password hash-password}
                                      command)))]
    (if (r/failed? result)
      (binding [*out* *err*]
        (println (:error result))
        (when (r/failed? parsed) (println parse/usage)))
      (run! println (:ok result)))
    (shutdown-agents)
    (System/exit (exit-code result))))
