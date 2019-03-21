(ns lein-classpath-jar.plugin
  (:require [robert.hooke]
            [leiningen.core.classpath]
            [clojure.string :refer [blank?]]
            [leiningen.core.main :refer [debug]]
            [clojure.java.io :as jio]
            [clojure.string :as str])
  (:import (java.security MessageDigest)
           (java.nio.file Files Paths Path LinkOption)
           (java.util.jar JarOutputStream Attributes$Name Manifest)
           (java.io File)))

;; cf https://github.com/nickgieschen/lein-extend-cp/blob/master/src/lein_extend_cp/plugin.clj

(defn md5 [string]
  (doto (MessageDigest/getInstance "MD5")
    (.digest (.getBytes string))))

(defn classpath-jar
  [classpath]
  (str (md5 classpath) ".jar"))

(defn ^Path jar-path-for-classpath [classpath]
  ;; TODO: implement me
  (Paths/get (into-array String [(System/getProperty "user.home")
                                 ".cpcache"
                                 (classpath-jar classpath)])))

(defn- manifest-classpath
  "Translate the classpath into a format suitable for a jar manifest, by changing all
  of the paths to file:// URIs and separating the list by single spaces."
  [classpath]
  (->> (for [path (str/split classpath (re-pattern File/pathSeparator))]
        (-> path jio/file .getAbsoluteFile .toURI .toString))
       (str/join " ")))

(defn create-classpath-jar!
  "Create a \"pathing jar\" - a jar file containing only a classpath in its manifest,
  useful in systems where command-line arguments have a limited length."
  [^Path jar-path classpath]
  (let [manifest (Manifest.)]
   (doto (.getMainAttributes manifest)
    (.put Attributes$Name/CLASS_PATH (manifest-classpath classpath))
    (.put Attributes$Name/MANIFEST_VERSION "1.0"))
   (doto (JarOutputStream. (jio/output-stream (.toUri jar-path) manifest))
    (.setLevel JarOutputStream/STORED)
    (.flush)
    (.close))
   jar-path))

(defn cache-classpath-jar [f & args]
  (let [classpath (apply f args)
        jar-path (jar-path-for-classpath classpath)]
    (when-not (Files/exists jar-path nil)
      (create-classpath-jar! jar-path classpath))
    (-> jar-path .toAbsolutePath .toString)))

(defn hooks []
  (robert.hooke/add-hook #'leiningen.core.classpath/get-classpath
                         #'cache-classpath-jar))
