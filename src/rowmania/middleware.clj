(ns rowmania.middleware
  (:require [clojure.string :as str]
            [rowmania.db :as db]))

(defn wrap-current-user
  "Legt den angemeldeten Nutzer (aus der Session) als :current-user in die Anfrage."
  [handler {:keys [db-value]}]
  (fn [request]
    (let [user (some->> (get-in request [:session :user-id]) (db/find-user-by-id (db-value)))]
      (handler (cond-> request user (assoc :current-user user))))))

(defn require-login
  "Leitet Anfragen ohne angemeldeten Nutzer zur Anmeldung um."
  [handler]
  (fn [request]
    (if (:current-user request)
      (handler request)
      {:status 303 :headers {"Location" "/login"}})))

(def ^:private safe-methods #{:get :head :options})

(defn- same-origin? [{:keys [headers]}]
  (let [fetch-site (get headers "sec-fetch-site")
        origin     (get headers "origin")
        host       (get headers "host")]
    (cond
      fetch-site (contains? #{"same-origin" "none"} fetch-site)
      origin     (= host (second (re-find #"^[a-z]+://([^/]+)" origin)))
      :else      true)))

(defn wrap-same-origin
  "Lehnt schreibende Anfragen von fremden Seiten ab (zusätzlich zu SameSite=Strict)."
  [handler]
  (fn [request]
    (if (or (contains? safe-methods (:request-method request)) (same-origin? request))
      (handler request)
      {:status 403 :headers {"Content-Type" "text/plain"} :body "Forbidden"})))

(def ^:private content-security-policy
  (str/join "; " ["default-src 'self'"
                  "style-src 'self' https://fonts.bunny.net"
                  "style-src-attr 'unsafe-inline'"
                  "font-src https://fonts.bunny.net"
                  "form-action 'self'"
                  "frame-ancestors 'none'"
                  "base-uri 'none'"]))

(defn wrap-security-headers [handler]
  (fn [request]
    (some-> (handler request)
            (update :headers merge {"Content-Security-Policy" content-security-policy
                                    "X-Content-Type-Options" "nosniff"
                                    "Referrer-Policy" "same-origin"}))))
