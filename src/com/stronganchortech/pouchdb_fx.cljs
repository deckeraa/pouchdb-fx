(ns com.stronganchortech.pouchdb-fx
  (:require
   [clojure.spec.alpha :as s]
   [re-frame.core :as rf]
   ["pouchdb" :as pouchdb]))

(defonce ^:private dbs (atom {}))

(defn create-or-open-db!
  "Uses the PouchDB initializer to open an existing database or create a new one.
  Pouch doesn't tell us whether the database existed or not, so we just return the db.
  https://pouchdb.com/api.html#create_database"
  [db-name]
  (let [new-dbs (swap! dbs (fn [dbs]
                             (if (get dbs db-name)
                               dbs
                               (assoc dbs db-name {:db-obj (pouchdb. db-name)}))))]
    (get-in new-dbs [db-name :db-obj])))

(defn db-obj [db]
  (if (or (keyword? db) (string? db))
    (or (get-in @dbs [db :db-obj]) ;; grab it from the cache
        (create-or-open-db! db)) ;; or open/create a new one
    db))

(defn attach-change-watcher! [db-name options change-handler]
  (create-or-open-db! db-name) ;; make sure that the db is created before attaching a change handler.
  (swap! dbs (fn [dbs]
               (let [old-watcher (get-in dbs [db-name :change-watcher])
                     db-obj (get-in dbs [db-name :db-obj])
                     change-handler (if (keyword? change-handler)
                                      #(rf/dispatch [change-handler (js->clj % :keywordize-keys true)])
                                      change-handler)]
                 (when old-watcher (.cancel old-watcher))
                 (assoc-in dbs [db-name :change-watcher]
                           (.on (.changes db-obj (clj->js options)) "change" change-handler))))))

(defn cancel-watcher! [db-name]
  (let [watcher (get-in @dbs [db-name :change-watcher])]
    (when watcher (.cancel watcher))))

(defn sync!
  "The following event keywords are accepted in handlers:
  :denied
  :paused
  :active
  :change
  :complete
  :error
  "
  ([db-name target options]
   (sync! db-name target options {}))
  ([db-name target options handlers]
   (swap! dbs (fn [dbs]
                (let [old-sync-obj (get-in dbs [db-name :sync-obj])]
                  (when old-sync-obj (.cancel old-sync-obj))
                  (println "About to call .sync" pouchdb (name db-name) target (clj->js options))
                  (let [sync-obj (.sync pouchdb (name db-name) target (clj->js options))]
                    (println "sync-obj: " sync-obj)
                    (doall (map (fn [[k v]]
                                  ;;(println "Calling .on with " sync-obj (name k) v)
                                  (.on sync-obj (name k) v))
                                handlers))
                    (assoc-in dbs [db-name :sync-obj] sync-obj)))))))

(defn cancel-sync!
  [db-name]
  (swap! dbs (fn [dbs]
               (when-let [sync-obj (get-in dbs [db-name :sync-obj])]
                 (println "Calling .cancel on sync-obj: " sync-obj)
                 (.cancel sync-obj)))))

(defn attach-success-and-failure-to-promise [promise success failure]
  (let [success (if (keyword? success)
                  #(rf/dispatch [success (js->clj % :keywordize-keys true)])
                  success)
        failure (if (keyword? failure)
                  #(rf/dispatch [failure (js->clj % :keywordize-keys true)])
                  failure)]
    (cond
      ;; attach both
      (and success failure)
      (.catch (.then promise success) failure)
      ;; attach success
      success
      (.then promise success)
      ;; attach failure
      failure
      (.catch promise failure)
      ;; attach neither
      :default
      promise)))

(rf/reg-fx
 :pouchdb
 (fn [{:keys [method db doc docs options success failure] :as request}]
   (let [db (or (db-obj db)
                (throw (js/Error. (str "PouchDB " db " not found." @dbs))))
         options (or options {})]
     (case method
       :all-docs
       (attach-success-and-failure-to-promise
        (.allDocs db (clj->js options))
        success failure)
       :bulk-docs
       (attach-success-and-failure-to-promise
        (.bulkDocs db (clj->js docs) (clj->js options))
        success failure)
       ;;
       :put
       (attach-success-and-failure-to-promise
        (.put db (clj->js doc) (clj->js options))
        success failure)
       ;;
       :post
       (attach-success-and-failure-to-promise
        (.post db (clj->js doc) (clj->js options))
        success failure)
       ;;
       :remove
       (attach-success-and-failure-to-promise
        (.remove db (clj->js doc) (clj->js options))
        success failure)
       ;;
       :destroy
       (attach-success-and-failure-to-promise
        (.destroy db) ;; TODO something with the promise isn't working: Uncaught (in promise) Error: database is destroyed
        success failure)
       ;;
       :cancel-sync!
       (cancel-sync! db)
       ;;
       (throw (js/Error. (str "The requested method: " method " is not supported by com.stronganchortech.pouchdb-fx.")))
       ))))

(rf/reg-event-fx
 :pouchdb
 (fn [_ [event args]]
   {:pouchdb args}))
