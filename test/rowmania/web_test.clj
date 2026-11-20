(ns rowmania.web-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [datahike.api :as d]
            [rowmania.db :as db]
            [rowmania.server :as server]
            [rowmania.test-db :refer [fresh-conn]]
            [rowmania.values :as v]))

(defn- date [s] (:ok (v/parse-iso-date s)))

(defn- fixture []
  (let [conn (fresh-conn)]
    (d/transact conn (concat (db/create-user-tx {:id (random-uuid) :name "ada" :password-hash "hash:geheim"})
                             (db/create-challenge-tx {:id (random-uuid) :name "Herbst" :start (date "2026-10-01")
                                                      :end (date "2026-12-31") :target-distance 600000})))
    (d/transact conn (db/add-session-tx {:id (random-uuid) :date (date "2026-10-05") :distance 5000
                                         :user-eid (db/find-entity @conn :user {:name "ada"})
                                         :challenge-eid (db/find-entity @conn :challenge {:name "Herbst"})}))
    {:conn conn
     :app (server/app {:db {:db-value (fn [] @conn) :transact! #(d/transact conn %)}
                       :verify-password (fn [pw h] (= (str "hash:" pw) h))
                       :new-id random-uuid
                       :today (constantly (date "2026-11-19"))}
                      {:cookie-secure false})}))

(defn- form-body [form]
  (java.io.ByteArrayInputStream. (.getBytes (str/join "&" (map (fn [[k v]] (str k "=" v)) form)))))

(defn- request [app method uri & {:keys [cookie form headers]}]
  (app (cond-> {:request-method method :uri uri :headers (merge {"host" "x"} headers)}
         cookie (assoc-in [:headers "cookie"] cookie)
         form   (-> (assoc :body (form-body form))
                    (assoc-in [:headers "content-type"] "application/x-www-form-urlencoded")))))

(defn- session-cookie [response]
  (some-> (get-in response [:headers "Set-Cookie"]) first (str/split #";") first))

(defn- login [app]
  (session-cookie (request app :post "/login" :form {"name" "ada" "password" "geheim"})))

(deftest anonymous-visitors-are-sent-to-login
  (let [{:keys [app]} (fixture)]
    (is (= "/login" (get-in (request app :get "/") [:headers "Location"])))
    (is (= 200 (:status (request app :get "/login"))))))

(deftest wrong-password-is-rejected
  (let [{:keys [app]} (fixture)
        response (request app :post "/login" :form {"name" "ada" "password" "falsch"})]
    (is (= 401 (:status response)))
    (is (nil? (session-cookie response)))))

(deftest cross-site-posts-are-rejected
  (let [{:keys [app]} (fixture)]
    (is (= 403 (:status (request app :post "/login" :headers {"sec-fetch-site" "cross-site"}
                                 :form {"name" "ada" "password" "geheim"}))))))

(deftest login-shows-challenge-and-sessions
  (let [{:keys [app]} (fixture)
        cookie (login app)
        overview (request app :get "/" :cookie cookie)
        sessions (request app :get "/sessions" :cookie cookie)]
    (is (some? cookie))
    (is (str/includes? (:body overview) "Herbst"))
    (is (str/includes? (:body overview) "von 600 km"))
    (is (str/includes? (:body sessions) "Mo, 5. Oktober"))))

(deftest session-can-be-added-and-edited
  (let [{:keys [app conn]} (fixture)
        cookie (login app)
        added (request app :post "/sessions" :cookie cookie
                       :form {"challenge" (:challenge/id (db/pull-challenge @conn (db/find-entity @conn :challenge {:name "Herbst"})))
                              "km" "6,5" "date" "2026-11-19"})
        session-id (d/q '[:find ?id . :where [?s :session/distance 6500] [?s :session/id ?id]] @conn)]
    (is (= 303 (:status added)))
    (is (some? session-id))
    (is (= 303 (:status (request app :post (str "/sessions/" session-id) :cookie cookie
                                 :form {"km" "7" "date" "2026-11-19"}))))
    (is (= 7000 (d/q '[:find ?m . :where [?s :session/date #inst "2026-11-19"] [?s :session/distance ?m]] @conn)))
    (is (= 422 (:status (request app :post (str "/sessions/" session-id) :cookie cookie
                                 :form {"km" "7" "date" "2027-01-01"}))))))

(deftest every-user-sees-every-challenge
  (let [{:keys [app conn]} (fixture)]
    (d/transact conn (db/create-user-tx {:id (random-uuid) :name "bob" :password-hash "hash:pw"}))
    (let [cookie  (session-cookie (request app :post "/login" :form {"name" "bob" "password" "pw"}))
          overview (:body (request app :get "/" :cookie cookie))]
      (is (str/includes? overview "Herbst"))
      (is (str/includes? overview "bob (du)"))
      (is (str/includes? overview "ada"))
      (is (str/includes? (:body (request app :get "/sessions" :cookie cookie)) "Herbst")))))
