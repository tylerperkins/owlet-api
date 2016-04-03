(ns owlet-cms.routes.api
  (:require [owlet-cms.db.core :refer [*db*] :as db])
  (:require [compojure.core :refer [defroutes GET PUT]]
            [ring.util.http-response :refer :all]
            ;; [ring.handler.dump :refer [handle-dump]]
            [compojure.api.sweet :refer [context]]
            [clojure.java.jdbc :as jdbc]))

(defn is-not-social-login-but-verified? [user]
  (let [identity0 (first (:identities user))]
    (if (:isSocial identity0)
      true
      (if (:email_verified user)
        true
        false))))

(defn handle-user-insert-webhook [res]
  (let [user (get-in res [:params :user])
        ;; context (get-in res [:params :context])
        found (try
                (jdbc/with-db-transaction
                  [t-conn *db*]
                  (jdbc/db-set-rollback-only! t-conn)
                  (db/get-user {:id (:user_id user)}))
                (catch Exception e (str "caught e:" (.getNextException e))))]
    (clojure.pprint/pprint user)
    (if-not found
      (let [transact! (try
                        (if (is-not-social-login-but-verified? user)
                          (jdbc/with-db-transaction
                            [t-conn *db*]
                            (jdbc/db-set-rollback-only! t-conn)
                            (db/create-user!
                              {:id       (:user_id user)
                               :name     (:name user)
                               :nickname (:nickname user)
                               :email    (:email user)
                               :picture  (:picture user)})))
                        (catch Exception e (str "caught e:" (.getNextException e))))]
        ;; returns 1 if inserted
        (if transact!
          (ok user)
          (internal-server-error transact!)))
      (ok "user exists"))))

(defn handle-get-users [_]
  (let [users (jdbc/with-db-transaction
                [t-conn *db*]
                (jdbc/db-set-rollback-only! t-conn)
                (db/get-users))]
    (when users
      (ok {:data users}))))

(defn update-users-district-id! [res]
  (let [district_id (get-in res [:params :district-id])
        user_id (get-in res [:params :user-id])
        transaction! (jdbc/with-db-transaction
                       [t-conn *db*]
                       (jdbc/db-set-rollback-only! t-conn)
                       (db/update-user-district-id! {:district_id district_id
                                                     :id          user_id}))]
    (if (= 1 transaction!)
      (ok "updated user's district id")
      (do
        (println transaction!)
        (internal-server-error transaction!)))))

(defroutes api-routes
           (context "/api" []
                    (GET "/users" [] handle-get-users)
                    (PUT "/users-district-id" {params :params} update-users-district-id!)
                    (PUT "/webhook" {params :params} handle-user-insert-webhook)))


