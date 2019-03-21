(ns lein-classpath-jar.plugin
  (:require [robert.hooke]
            [leiningen.core.classpath]
            [clojure.string :refer [blank?]]
            [leiningen.core.main :refer [debug]]
            [clojure.java.io :as jio]
            [clojure.string :as string])
  (:import (java.security MessageDigest)
           (java.nio.file Files Paths Path)
           (java.util.jar JarOutputStream Attributes$Name Manifest)
           (java.io File)))

;; cf https://github.com/nickgieschen/lein-extend-cp/blob/master/src/lein_extend_cp/plugin.clj

;; Cribbing from https://github.com/tebeka/clj-digest/blob/master/src/digest.clj here
(defn md5
  "Produce an MD5 checksum similar to `B80AC5C8E229CFFD374E8A74168225CF`"
  [^String string]
  (let [digest (doto (MessageDigest/getInstance "MD5")
                 (.update (.getBytes string "UTF-8")))
        value  (BigInteger. 1 (.digest digest))]
    (format (str "%0" (* 2 (.getDigestLength digest)) "X") value)))


(defn classpath-jar
  [classpath]
  (str (md5 classpath) ".jar"))

(defn ^Path jar-path-for-classpath [classpath]
  (Paths/get (System/getProperty "user.dir")
             (into-array String [".cpcache"
                                 (classpath-jar classpath)])))

(defn- manifest-classpath
  "Translate the classpath into a format suitable for a jar manifest, by changing all
  of the paths to file:// URIs and separating the list by single spaces."
  [classpath]
  (->> (for [path (string/split classpath (re-pattern File/pathSeparator))]
        (-> path jio/file .getAbsoluteFile .toURI .toString))
       (string/join " ")))

(defn create-cpcache-dir!
  "Create the .cpcache directory (in the current directory, assumed to be the project root) if
  it doesn't exist."
  []
  (Files/createDirectories (Paths/get (System/getProperty "user.dir") (into-array [".cpcache"]))
                           (into-array java.nio.file.attribute.FileAttribute [])))

(defn create-classpath-jar!
  "Create a \"pathing jar\" - a jar file containing only a classpath in its manifest,
  useful in systems where command-line arguments have a limited length."
  [^Path jar-path classpath]
  (create-cpcache-dir!)
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
