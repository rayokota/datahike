(ns performance.connection
  (:require [performance.measure :refer [measure-connection-times]]
            [performance.db.api :as db]
            [performance.config :as c]
            [performance.common :refer [short-error-report int-linspace]]
            [incanter.io]
            [incanter.core :as ic])
  (:import (java.util UUID)))

(def schema [{:db/ident       :name
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}])


(defn create-n-transactions [n]
  (->> (repeatedly #(str (UUID/randomUUID)))
       (take n)
       (mapv (fn [id] {:name id}))))


(defn run-connection-measurements
  "Returns observation in following order:
   [:backend :schema-on-read :temporal-index :datoms :mean :sd]"
  [uris iterations]
  (println "Getting connection times...")
  (let [header [:backend :schema-on-read :temporal-index :datoms :mean :sd]
        res (for [d-count                                   ;;(int-linspace 0 5000 17)
                          (int-linspace 0 5000 21) ;; for 8192 memory exception
                  uri uris]
              (do
                (println " CONNECT: Number of datoms in db:" d-count " Uri:" uri)
                (try
                  (let [tx (create-n-transactions d-count)
                        sor (:schema-on-read uri)
                        ti (:temporal-index uri)]
                    (db/prepare-db (:lib uri) (:uri uri) (if sor [] schema) tx :schema-on-read sor :temporal-index ti)
                    (let [t (measure-connection-times (:lib uri) (:uri uri) iterations)]
                      (println "  Mean Time:" (:mean t) "ms")
                      (println "  Standard deviation:" (:sd t) "ms")
                      [(:name uri) sor ti d-count (:mean t) (:sd t)]))
                  (catch Exception e (short-error-report e))
                  (catch AssertionError e (short-error-report e))

                  )))]
    [header res]))


(defn get-connection-times [file-suffix iterations]
  (let [[header res] (run-connection-measurements (remove #(= "Datomic Mem" (:name %)) c/uris) iterations)
        data (ic/dataset header (remove nil? res))]
    (print "Save connection times...")
    (ic/save data (c/filename file-suffix))
    (print " saved\n")))
