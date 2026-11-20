(ns rowmania.routes
  "Handler und Routen. Alle Abhängigkeiten (DB, Passwortprüfung, UUIDs, Uhr) kommen als `deps`."
  (:require [reitit.ring :as ring]
            [rowmania.challenge-view :as challenge-view]
            [rowmania.db :as db]
            [rowmania.middleware :refer [require-login]]
            [rowmania.pages :as pages]
            [rowmania.result :as r]
            [rowmania.values :as values]))

(defn- html [status body]
  {:status status :headers {"Content-Type" "text/html; charset=utf-8"} :body body})

(defn- redirect [location] {:status 303 :headers {"Location" location}})

(defn- query-param [request name] (get-in request [:query-params name]))
(defn- form-param [request name] (get-in request [:form-params name]))
(defn- uuid-param [token] (some-> token parse-uuid))

;; ------------------------------------------------------------------ Login

(defn- login-form [_deps]
  (fn [request]
    (if (:current-user request) (redirect "/") (html 200 (pages/login-page {})))))

(defn- login-submit [{:keys [db verify-password]}]
  (fn [request]
    (let [name     (form-param request "name")
          user     (some->> name (db/find-login ((:db-value db))))
          verified (verify-password (form-param request "password") (:user/password-hash user))]
      (if (and user verified)
        {:status 303 :headers {"Location" "/"}
         ;; :recreate vergibt eine neue Session-ID und verhindert Session-Fixation
         :session (with-meta {:user-id (:user/id user)} {:recreate true})}
        (html 401 (pages/login-page {:error "Name oder Passwort stimmt nicht." :name name}))))))

(defn- logout [_deps]
  (fn [_] {:status 303 :headers {"Location" "/login"} :session nil}))

;; -------------------------------------------------------------- Challenge

(defn- challenge-page [{:keys [db today]}]
  (fn [{:keys [current-user] :as request}]
    (let [database   ((:db-value db))
          challenges (db/list-challenges database)
          selected   (challenge-view/choose-challenge challenges (today) (uuid-param (query-param request "challenge")))
          summary    (some-> selected
                             (as-> c (challenge-view/summarize
                                      {:challenge    c
                                       :participants (db/list-users database)
                                       :sessions     (db/challenge-sessions database (:challenge/id c))
                                       :today        (today)
                                       :me-id        (:user/id current-user)})))]
      (html 200 (pages/challenge-page {:summary summary :challenge-id (:challenge/id selected)})))))

;; --------------------------------------------------------------- Sessions

(defn- sessions-response
  [{:keys [db today]} {:keys [current-user]} {:keys [status selected-id editing-id error form] :or {status 200}}]
  (let [database   ((:db-value db))
        challenges (db/list-challenges database)
        selected   (challenge-view/choose-challenge challenges (today) selected-id)]
    (html status
          (pages/sessions-page
           {:challenges challenges :selected selected :today (today)
            :sessions   (when selected (db/user-sessions database (:user/id current-user) (:challenge/id selected)))
            :editing-id editing-id :error error :form form}))))

(defn- sessions-list [deps]
  (fn [request]
    (sessions-response deps request {:selected-id (uuid-param (query-param request "challenge"))
                                     :editing-id  (uuid-param (query-param request "edit"))})))

(defn- require-challenge [challenges challenge-id]
  (if-let [challenge (some #(when (= challenge-id (:challenge/id %)) %) challenges)]
    (r/ok challenge)
    (r/fail "Challenge nicht gefunden")))

(defn- require-date-in-period [{:challenge/keys [start end]} date]
  (if (and (not (neg? (compare date start))) (not (pos? (compare date end))))
    (r/ok date)
    (r/fail "Das Datum liegt außerhalb der Challenge.")))

(defn- parse-session-input
  "Liest Kilometer und Datum des Formulars gegen den Zeitraum der Challenge."
  [request challenge]
  (r/bind (values/parse-kilometres (form-param request "km"))
          (fn [distance]
            (r/bind (values/parse-iso-date (form-param request "date"))
                    (fn [date]
                      (r/fmap (require-date-in-period challenge date)
                              (fn [date] {:date date :distance distance})))))))

(defn- sessions-after
  "Schreibt bei gültiger Eingabe und leitet zur Liste um, sonst Seite mit Fehler."
  [{:keys [db] :as deps} request challenge-id plan-write]
  (let [database   ((:db-value db))
        user       (:current-user request)
        challenges (db/list-challenges database)
        outcome    (r/bind (require-challenge challenges challenge-id)
                           (fn [challenge]
                             (r/bind (parse-session-input request challenge)
                                     (fn [input] (plan-write database challenge input)))))]
    (if (r/failed? outcome)
      (sessions-response deps request {:status 422 :selected-id challenge-id :error (:error outcome)
                                       :form {:km (form-param request "km") :date (form-param request "date")}})
      (do ((:transact! db) (:ok outcome))
          (redirect (str "/sessions?challenge=" challenge-id))))))

(defn- sessions-add [{:keys [new-id] :as deps}]
  (fn [request]
    (let [user (:current-user request)]
      (sessions-after deps request (uuid-param (form-param request "challenge"))
                      (fn [database challenge {:keys [date distance]}]
                        (r/ok (db/add-session-tx {:id (new-id) :date date :distance distance
                                                  :user-eid (:db/id user)
                                                  :challenge-eid (:db/id challenge)})))))))

(defn- sessions-update [deps]
  (fn [request]
    (let [user      (:current-user request)
          session   (some->> (uuid-param (get-in request [:path-params :id]))
                             (db/find-session ((:db-value (:db deps)))))
          owned?    (= (:user/id user) (get-in session [:session/user :user/id]))]
      (if (and session owned?)
        (sessions-after deps request (get-in session [:session/challenge :challenge/id])
                        (fn [_ _ input] (r/ok (db/update-session-tx (:db/id session) input))))
        {:status 404 :headers {"Content-Type" "text/plain"} :body "Nicht gefunden"}))))

;; ----------------------------------------------------------------- Routen

(defn routes [deps]
  [["/login"        {:get (login-form deps) :post (login-submit deps)}]
   ["/logout"       {:post (logout deps)}]
   ["/"             {:get (require-login (challenge-page deps))}]
   ["/sessions"     {:get (require-login (sessions-list deps)) :post (require-login (sessions-add deps))}]
   ["/sessions/:id" {:post (require-login (sessions-update deps))}]
   ["/health"       {:get (fn [_] {:status 200 :headers {"Content-Type" "text/plain"} :body "ok"})}]
   ["/assets/*"     (ring/create-resource-handler {:root "public"})]])
