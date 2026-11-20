(ns rowmania.cli.plan
  "Aus einem Befehl und dem aktuellen DB-Stand wird ein Plan:
  {:ok {:tx [...] :report [zeilen]}} oder {:error text}. Rein; Zufall (UUIDs,
  Salt) kommt über `deps` herein, geschrieben wird erst von der Ausführung."
  (:require [rowmania.db :as db]
            [rowmania.result :as r]))

(defn- plan [tx report] (r/ok {:tx tx :report report}))

(defn- require-entity
  "Entity-ID oder Fehler. `kind` ist :user oder :challenge."
  [database kind reference]
  (if-let [eid (db/find-entity database kind reference)]
    (r/ok eid)
    (r/fail (str (case kind :user "Nutzer" :challenge "Challenge") " nicht gefunden: "
                 (or (:id reference) (:name reference))))))

(defn- require-free-name
  "Ok, wenn kein *anderer* Eintrag den Namen trägt."
  [database kind name owner-eid]
  (let [holder (db/find-entity database kind {:name name})]
    (if (and holder (not= holder owner-eid))
      (r/fail (str "Name bereits vergeben: " name))
      (r/ok name))))

(defn- require-period [{:challenge/keys [start end]}]
  (if (pos? (compare start end))
    (r/fail "Start liegt nach dem Ende")
    (r/ok nil)))

(def ^:private challenge-updates
  "Befehl -> [Attribut Argument]"
  {:update-challenge-name  [:challenge/name :name]
   :update-challenge-start [:challenge/start :start]
   :update-challenge-end   [:challenge/end :end]
   :update-challenge-goal  [:challenge/target-distance :goal]})

(defn- update-challenge-plan [database {:keys [command args]}]
  (let [[attribute argument] (challenge-updates command)
        value                (get args argument)]
    (r/bind (require-entity database :challenge (:challenge args))
            (fn [eid]
              (let [updated (assoc (db/pull-challenge database eid) attribute value)]
                (r/bind (require-free-name database :challenge (:challenge/name updated) eid)
                        (fn [_]
                          (r/bind (require-period updated)
                                  (fn [_] (plan (db/update-challenge-tx eid attribute value)
                                                [(str "Challenge aktualisiert: " (:challenge/name updated))]))))))))))

(defmulti plan-command (fn [_database command _deps] (:command command)))

(defmethod plan-command :add-user
  [database {{:keys [name password]} :args} {:keys [new-id hash-password]}]
  (r/bind (require-free-name database :user name nil)
          (fn [_] (let [id (new-id)]
                    (plan (db/create-user-tx {:id id :name name :password-hash (hash-password password)})
                          [(str "Nutzer angelegt: " name " " id)])))))

(defmethod plan-command :update-user-name
  [database {{:keys [user name]} :args} _]
  (r/bind (require-entity database :user user)
          (fn [eid] (r/bind (require-free-name database :user name eid)
                            (fn [_] (plan (db/rename-user-tx eid name) [(str "Nutzername geändert: " name)]))))))

(defmethod plan-command :update-user-pass
  [database {{:keys [user password]} :args} {:keys [hash-password]}]
  (r/bind (require-entity database :user user)
          (fn [eid] (plan (db/set-password-hash-tx eid (hash-password password))
                          ["Passwort geändert"]))))

(defmethod plan-command :list-users
  [database _ _]
  (plan [] (for [{:user/keys [id name]} (db/list-users database)] (str name " " id))))

(defmethod plan-command :create-challenge
  [database {{:keys [name start end goal]} :args} {:keys [new-id]}]
  (let [id (new-id)
        tx (db/create-challenge-tx {:id id :name name :start start :end end :target-distance goal})]
    (r/bind (require-free-name database :challenge name nil)
            (fn [_] (r/bind (require-period {:challenge/start start :challenge/end end})
                            (fn [_] (plan tx [(str "Challenge angelegt: " name " " id)])))))))

(doseq [command (keys challenge-updates)]
  (defmethod plan-command command [database command-data _] (update-challenge-plan database command-data)))

(defmethod plan-command :add-session
  [database {{:keys [challenge user date distance]} :args} {:keys [new-id]}]
  (r/bind (require-entity database :challenge challenge)
          (fn [challenge-eid]
            (r/bind (require-entity database :user user)
                    (fn [user-eid]
                      (plan (db/add-session-tx {:id (new-id) :date date :distance distance
                                                :user-eid user-eid :challenge-eid challenge-eid})
                            ["Session eingetragen"]))))))
