(ns rowmania.db
  "Datahike auf SQLite. Gibt eine Map von Funktionen zurück, nicht die Verbindung."
  (:require [datahike.api :as d]
            [datahike-sqlite.core]
            [integrant.core :as ig]
            [rowmania.db-schema :refer [schema]]))

(defn create-user-tx [{:keys [id name password-hash]}]
  [{:user/id id :user/name name :user/password-hash password-hash}])

(defn rename-user-tx [user-eid name]
  [{:db/id user-eid :user/name name}])

(defn set-password-hash-tx [user-eid password-hash]
  [{:db/id user-eid :user/password-hash password-hash}])

(defn create-challenge-tx [{:keys [id name start end target-distance]}]
  [{:challenge/id id :challenge/name name :challenge/start start
    :challenge/end end :challenge/target-distance target-distance}])

(defn update-challenge-tx
  "`attribute` ist eines von :challenge/name, :challenge/start, :challenge/end,
  :challenge/target-distance."
  [challenge-eid attribute value]
  [{:db/id challenge-eid attribute value}])

(defn add-session-tx
  "Wer eine Session einträgt, nimmt damit an der Challenge teil."
  [{:keys [id date distance user-eid challenge-eid]}]
  [{:session/id id :session/date date :session/distance distance
    :session/user user-eid :session/challenge challenge-eid}
   [:db/add challenge-eid :challenge/participants user-eid]])

(defn find-entity
  "Entity-ID des Nutzers bzw. der Challenge (`kind` ist :user oder :challenge).
  `reference` ist {:id uuid} oder {:name string}; nil, wenn es sie nicht gibt."
  [db kind reference]
  (let [attribute (keyword (name kind) (if (:id reference) "id" "name"))
        value     (or (:id reference) (:name reference))]
    (d/q '[:find ?e . :in $ ?a ?v :where [?e ?a ?v]] db attribute value)))

(defn list-users
  "Alle Nutzer als [{:user/id .. :user/name ..}], nach Name sortiert."
  [db]
  (->> (d/q '[:find [(pull ?u [:user/id :user/name]) ...] :where [?u :user/id]] db)
       (sort-by :user/name)))

(def ^:private challenge-pattern
  [:db/id :challenge/id :challenge/name :challenge/start :challenge/end :challenge/target-distance])

(defn pull-challenge
  "`ref` ist eine Entity-ID oder [:challenge/id uuid]."
  [db ref]
  (d/pull db challenge-pattern ref))

(defn find-login
  "Nutzer samt Passwort-Hash für die Anmeldung; nil, wenn der Name unbekannt ist."
  [db name]
  (d/q '[:find (pull ?u [:db/id :user/id :user/name :user/password-hash]) .
         :in $ ?name :where [?u :user/name ?name]]
       db name))

(defn find-user-by-id [db id]
  (d/q '[:find (pull ?u [:db/id :user/id :user/name]) .
         :in $ ?id :where [?u :user/id ?id]]
       db id))

(defn list-challenges
  "Alle Challenges. Alle Nutzer nehmen an allen Challenges teil."
  [db]
  (vec (d/q [:find [(list 'pull '?c challenge-pattern) '...] :where ['?c :challenge/id]] db)))

(defn challenge-sessions
  "Alle Sessions einer Challenge samt Nutzer."
  [db challenge-id]
  (vec (d/q '[:find [(pull ?s [:session/id :session/date :session/distance
                               {:session/user [:user/id :user/name]}]) ...]
              :in $ ?cid
              :where [?c :challenge/id ?cid] [?s :session/challenge ?c]]
            db challenge-id)))

(defn user-sessions
  "Sessions eines Nutzers in einer Challenge, neueste zuerst."
  [db user-id challenge-id]
  (->> (d/q '[:find [(pull ?s [:session/id :session/date :session/distance]) ...]
              :in $ ?uid ?cid
              :where [?u :user/id ?uid] [?c :challenge/id ?cid]
                     [?s :session/user ?u] [?s :session/challenge ?c]]
            db user-id challenge-id)
       (sort-by :session/date #(compare %2 %1))
       vec))

(defn find-session [db session-id]
  (d/q '[:find (pull ?s [:db/id :session/id :session/date :session/distance
                         {:session/user [:user/id]} {:session/challenge [:challenge/id]}]) .
         :in $ ?id :where [?s :session/id ?id]]
       db session-id))

(defn update-session-tx [session-eid {:keys [date distance]}]
  [{:db/id session-eid :session/date date :session/distance distance}])

(defn- ensure-directory!
  "SQLite legt Verzeichnisse nicht selbst an."
  [dbname]
  (some-> (java.io.File. ^String dbname) .getAbsoluteFile .getParentFile .mkdirs))

(defn- ensure-database! [cfg]
  (ensure-directory! (get-in cfg [:store :dbname]))
  (when-not (d/database-exists? cfg)
    (d/create-database cfg))
  (d/connect cfg))

(defn init-db
  "Öffnet die Datenbank. Gibt Lese-/Schreibfunktionen zurück, nicht die Verbindung."
  [cfg]
  (let [conn (ensure-database! cfg)]
    (d/transact conn schema)
    {:db-value  (fn [] @conn)
     :transact! (fn [tx] (d/transact conn tx))
     :close     (fn [] (d/release conn))}))

(defn refreshing
  "Variante für langlebige Prozesse. Datahike hält den Stand im Speicher; was ein
  anderer Prozess (z. B. das CLI) in die Datei schreibt, sähe der Server sonst nie,
  und ein Schreibzugriff auf veraltetem Stand würde fremde Änderungen überschreiben.
  Daher wird vor jedem Lesen und Schreiben neu verbunden (~10 ms)."
  [cfg]
  (let [lock    (Object.)
        current (atom (d/connect cfg))
        fresh!  (fn [] (swap! current (fn [conn] (d/release conn) (d/connect cfg))))]
    {:db-value  (fn [] (locking lock @(fresh!)))
     :transact! (fn [tx] (locking lock (d/transact (fresh!) tx)))
     :close     (fn [] (d/release @current))}))

(defmethod ig/init-key :rowmania/db
  [_ {:keys [config]}]
  (let [cfg (:db config)]
    ((:close (init-db cfg))) ; legt Datei und Schema an
    (refreshing cfg)))

(defmethod ig/halt-key! :rowmania/db
  [_ {:keys [close]}]
  (close))
