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
  (println "create-or-open-db!" db-name dbs)
  (let [new-dbs (swap! dbs (fn [dbs]
                             (if (get dbs db-name)
                               dbs
                               (assoc dbs db-name {:db-obj (pouchdb. db-name)}))))]
    (get-in new-dbs [db-name :db-obj])))

(defn db-obj
  "Given a database object or a database name, will return the corresponding database object."
  [db]
  (if (or (keyword? db) (string? db))
    (or (get-in @dbs [db :db-obj]) ;; grab it from the cache
        (create-or-open-db! db)) ;; or open/create a new one
    db))

(defn attach-change-watcher!
  "Attaches the provided change-handler to the db specified by db-name.
   Cancels existing watchers attached to the db."
  [db-name options change-handler]
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

(defn cancel-watcher!
  "Cancels all existing watchers on the db specified by db-name."
  [db-name]
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
                  (println "About to call .sync" pouchdb db-name target (clj->js options))
                  (let [sync-obj (.sync pouchdb db-name target (clj->js options))]
                    (println "sync-obj: " sync-obj)
                    (doall (map (fn [[k v]]
                                  (.on sync-obj (name k) v))
                                handlers))
                    (assoc-in dbs [db-name :sync-obj] sync-obj)))))))

(defn cancel-sync!
  [db-name]
  (println "In cancel-sync for " db-name @dbs)
  (swap! dbs (fn [dbs]
               (when-let [sync-obj (get-in dbs [db-name :sync-obj])]
                 (println "Calling .cancel on sync-obj: " sync-obj)
                 (.cancel sync-obj)))))

(defn- attach-success-and-failure-to-promise
  "Takes a promise and attaches optional success and failure handlers."
  [promise success failure]
  (let [success (if (keyword? success)
                  #(rf/dispatch [success (js->clj % :keywordize-keys true)])
                  #(success (js->clj % :keywordize-keys true)))
        failure (if (keyword? failure)
                  #(rf/dispatch [failure (js->clj % :keywordize-keys true)])
                  #(failure (js->clj % :keywordize-keys true)))]
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
 (fn [{:keys [method db doc docs doc-id attachment-id rev attachment attachment-type target-url options success failure handler handlers] :as request}]
   (let [db-name (when (string? db) db)
         db (or (db-obj db) ;; set db to be the actual db object
                (if db
                  (throw (js/Error. (str "PouchDB " db " not found." @dbs)))
                  (throw (js/Error. (str ":db needs to be specified in: " request)))))
         options (or options {})
         doc-id (or doc-id (:_id doc))  ;; enable devs to pass a doc instead of pulling the id out
         rev    (or rev    (:_rev doc)) ;; enable devs to pass a doc instead of pulling the rev out
         ]
     (case method
       ;;
       :destroy
       (attach-success-and-failure-to-promise
        (.destroy db) ;; TODO something with the promise isn't working: Uncaught (in promise) Error: database is destroyed
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
       :get
       (attach-success-and-failure-to-promise
        (.get db doc-id (clj->js options))
        success failure)
       ;;
       :remove
       (attach-success-and-failure-to-promise
        (.remove db (clj->js doc) (clj->js options))
        success failure)
       ;;
       :bulk-docs
       (attach-success-and-failure-to-promise
        (.bulkDocs db (clj->js docs) (clj->js options))
        success failure)
       ;;
       :all-docs
       (attach-success-and-failure-to-promise
        (.allDocs db (clj->js options))
        success failure)
       ;;
       :attach-change-watcher!
       (attach-change-watcher! db-name (clj->js options) handler)
       ;; Note: The changes function isn't handler here since it's handled separately in attach-change-watcher!. TODO: Is this the right design decision? 
       ;; 
       ;; TODO replicate
       :sync!
       (sync! db-name target-url options handlers)
       ;; TODO return information on the sync objects that have been configured -- e.g. what database you are syncing to.
       ;;
       :cancel-sync!
       (cancel-sync! db-name)
       ;;
       ;; TODO test putAttachment
       :put-attachment
       (attach-success-and-failure-to-promise
        (.putAttachment db doc-id attachment-id rev attachment attachment-type)
        success failure)
       ;;
       ;; TODO test getAttachment
       :get-attachment
       (attach-success-and-failure-to-promise
        (.getAttachment db doc-id attachment-id (clj->js options))
        success failure)
       ;;
       ;; TODO test removeAttachment
       :remove-attachment
       (attach-success-and-failure-to-promise
        (.removeAttachment db doc-id attachment-id rev)
        success failure)
       ;; TODO createIndex -- needs the pouchdb-find plugin?
       ;; TODO find
       ;; TODO explain
       ;; TODO getIndexes
       ;; TODO deleteIndex
       ;; TODO query
       ;; TODO viewCleanup
       ;; TODO info
       ;; TODO compact
       ;; TODO revsDiff
       ;; TODO bulkGet
       ;; TODO close
       ;;
       (throw (js/Error. (str "The requested method: " method " is not supported by com.stronganchortech.pouchdb-fx.")))
       ))))

(rf/reg-event-fx
 :pouchdb
 (fn [_ [event args]]
   {:pouchdb args}))
