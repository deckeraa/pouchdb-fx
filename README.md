# pouchdb-fx

pouchdb-fx is a middleware effects library used to hook up PouchDB to re-frame.

## Basic Usage

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
TODO

To pass options listed in the [PouchDB API][https://pouchdb.com/api.html], use the :options keyword and TODO

Databases are referred to in the :db field using a database name which can be either a string or a keyword.

You don't need to create a database before using it. The first time you dispatch a :pouchdb event
with a db name, the library will create the PouchDB object and cache it locally for subsequent uses during the session.

## Recommended Patterns
TODO write about :load-all-pouch and how to use this with re-frame subs.

## License

Copyright © 2020 Aaron Decker stronganchortech.com

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
