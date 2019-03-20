(ns lein-classpath-jar.plugin
  (:require [robert.hooke]
            [leiningen.core.classpath]
            [clojure.string :refer [blank?]]
            [leiningen.core.main :refer [debug]])))

;; cf https://github.com/nickgieschen/lein-extend-cp/blob/master/src/lein_extend_cp/plugin.clj

(defn create-pathing-jar [classpath]
  ;; TODO: implement me
  classpath)

(defn cache-classpath-jar [f & args]
  (let [classpath (apply f args)
    (create-pathing-jar classpath)))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.core.classpath/get-classpath
                         #'cache-classpath-jar))
