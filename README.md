# pouchdb-fx

pouchdb-fx is a middleware effects library used to hook up PouchDB to re-frame.

## Basic Usage

Add the following dependency to your project.clj:
```
[com.stronganchortech/pouchdb-fx "0.1.0-SNAPSHOT"]
```

You will also need to install pouchdb and the pouchdb-find plugin into your project.
How you do this depends on your setup. If you are using the re-frame lein template,
you may simply `npm i pouchdb pouchdb-find`.

pouchdb-fx registers the :pouchdb event handler in re-frame when you include the library.

:require the following into your namespace:
```
[com.stronganchortech.pouchdb-fx :as pouchdb-fx]
```

Then you can dispatch :pouchdb events to interact with PouchDB.
For example, here's a button that cancels the syncing on the PouchDB database name "my-database-name".
```
[:button {:on-click #(re-frame/dispatch [:pouchdb {:method :cancel-sync!
                                                   :db "my-database-name"}])}
 "Cancel Sync"]
```

Here's a Reagent component that will create a new document based on the text in a input field:
```
(defn create-note []
  (let [text (reagent/atom "")]
    (fn []
      [:div
       [:h3 "Create a document using post"]
       [:input {:type :text :value @text
                :on-change #(reset! text (-> % .-target .-value))}]
       [:button {:on-click #(re-frame/dispatch
                             [:pouchdb
                              {:db "example"
                               :method :post
                               :doc {:type "note" :text @text}
                               }])}
        "Create document"]])))
```

To determine what key-values to pass in,
simply refer to the :pouchdb fx handler in [pouchdb_fx.cljs](https://github.com/deckeraa/pouchdb-fx/blob/master/src/com/stronganchortech/pouchdb_fx.cljs) and to the [PouchDB API docs](https://pouchdb.com/api.html).
There is also example usage in the [pouchdb-fx-examples project](https://github.com/deckeraa/pouchdb-fx-examples).

Databases are referred to in the :db field using a database name which must be a string.
Keyword database names are not supported at this time.

You don't need to create a database before using it. The first time you dispatch a :pouchdb event
with a db name, the library will create the PouchDB object and cache it locally for subsequent uses during the session.

## Keeping re-frame in sync with what's in PouchDB.
I recommend the following pattern.
In your events.cljs, use a defonce to create your database and attach a change handler that
dispatches a :load-from-pouch event any time there is a change:
```
(defonce setup-watcher
  (pouchdb-fx/attach-change-watcher!
   "the-name-of-your-database"
   {:since "now" :live true}
   (fn [v]
     (re-frame/dispatch [:load-from-pouch]) 
     )))
```

You can then create a pair of event handlers to do the doc load and set it into the app-db:
```
(re-frame/reg-event-fx
 :load-from-pouch
 (fn [{:keys [db pouchdb-docs]} [_ add-sync?]]
   {:pouchdb
    {:db "the-name-of-your-database"
     :method :all-docs
     :options {:include_docs true}
     :success
     (fn [v]
       (re-frame/dispatch [:pouchdb-alldocs-success (js->clj v :keywordize-keys true)])
       )}}))

(re-frame/reg-event-fx
 :pouchdb-alldocs-success
 (fn [{:keys [db]} [_  all-docs]]
   (let [docs (mapv :doc (:rows all-docs))]
      {:db (assoc db :docs docs)})))
```

Then your subscriptions functions in subs.cljs will just read out of the app-db node that
:pouchdb-alldocs-success dumped everything into.

This works well for small apps where the entire PouchDB can be loaded in totality into an app-db node.
For larger apps, add logic to the change-watcher to dispatch events based off of the type of document
changed, and in these various event handlers use query or find to pull back just the relevant documents into your app-db node.


## License

Copyright © 2020 Aaron Decker [stronganchortech.com](stronganchortech.com)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
