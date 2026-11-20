(ns rowmania.server
  (:require [integrant.core :as ig]
            [org.httpkit.server :as http]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [rowmania.middleware :as middleware]
            [rowmania.passwords :as passwords]
            [rowmania.routes :as routes]
            [rowmania.values :as values]))

(def ^:private session-max-age (* 14 24 60 60))

(defn app
  "Der vollständige Ring-Handler. `deps`: {:db :verify-password :new-id :today}."
  [{:keys [db] :as deps} {:keys [cookie-secure]}]
  (-> (ring/ring-handler (ring/router (routes/routes deps))
                         (ring/create-default-handler))
      (middleware/wrap-current-user db)
      middleware/wrap-same-origin
      middleware/wrap-security-headers
      wrap-params
      (wrap-session {:store        (memory-store)
                     :cookie-name  "rowmania-session"
                     :cookie-attrs {:http-only true :secure cookie-secure :same-site :strict
                                    :max-age session-max-age}})))

(defmethod ig/init-key :rowmania/server
  [_ {:keys [config db]}]
  (http/run-server
   (app {:db db
         :verify-password passwords/verify-password
         :new-id random-uuid
         :today (fn [] (values/today-in "Europe/Berlin"))}
        config)
   (select-keys config [:host :port])))

(defmethod ig/halt-key! :rowmania/server
  [_ stop-server]
  (stop-server :timeout 100))
