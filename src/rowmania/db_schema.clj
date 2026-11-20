(ns rowmania.db-schema
  "Datahike-Schema von RowMania.

  Daten (`start`, `end`, `date`) sind `:db.type/instant`, da Datahike keinen
  reinen Datumstyp kennt. Entfernungen (`target-distance`, `distance`) sind
  ganze Meter.")

(def challenge-schema
  [{:db/ident       :challenge/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :challenge/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/value}
   {:db/ident       :challenge/start
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :challenge/end
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :challenge/target-distance
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident       :challenge/participants
    :db/doc         "Nutzer, die an der Challenge teilnehmen."
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}])

(def user-schema
  [{:db/ident       :user/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :user/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/value}
   {:db/ident       :user/password-hash
    :db/doc         "Salted Hash des Passworts, nie das Passwort selbst."
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}])

(def session-schema
  [{:db/ident       :session/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :session/date
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :session/distance
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident       :session/user
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :session/challenge
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}])

(def schema
  (vec (concat challenge-schema user-schema session-schema)))
