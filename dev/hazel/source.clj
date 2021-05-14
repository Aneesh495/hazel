(ns hazel.source
  "Source manifests bind numerical evidence to implementation and test bytes."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hazel.io.edn :as edn]))

(defn source-files []
  (let [root (.toPath (.getCanonicalFile (io/file ".")))]
    (->> (concat (mapcat #(let [dir (io/file %)] (when (.exists dir) (file-seq dir)))
                        ["src" "test" "dev" "resources" "examples" "bin" ".github"])
                 (map io/file ["deps.edn" "Makefile"]))
         (filter #(.isFile %))
         (map (fn [file]
                (let [path (.toPath (.getCanonicalFile file))]
                  [(str/replace (str (.relativize root path)) "\\" "/") file])))
         (sort-by first) vec)))

(defn manifest []
  (into (sorted-map)
        (for [[path file] (source-files)]
          [path {:bytes (.length file) :sha256 (edn/sha256-bytes (java.nio.file.Files/readAllBytes (.toPath file)))}])))

(defn fingerprint [] (edn/sha256 (manifest)))
