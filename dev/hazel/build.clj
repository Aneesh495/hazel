(ns hazel.build
  "Build an executable dependency-complete JAR using only Clojure and JDK tools."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hazel.io.edn :as edn]
            [hazel.source :as source])
  (:import [java.util.jar JarFile JarEntry JarOutputStream]
           [java.nio.file Files]))

(defn- files-under [directory]
  (let [dir (.toPath (.getCanonicalFile (io/file directory)))]
    (->> (file-seq (.toFile dir))
         (filter #(.isFile %))
         (map #(vector (str/replace (str (.relativize dir (.toPath (.getCanonicalFile %)))) "\\" "/") %))
         (sort-by first))))

(defn build! []
  (let [classes (io/file "target/classes")
        _ (when (.exists classes) (doseq [file (reverse (file-seq classes))] (io/delete-file file)))
        _ (.mkdirs classes)
        source-before (source/fingerprint)]
    (binding [*compile-path* (.getPath classes)] (compile 'hazel.main))
    (let [destination (io/file "target/hazel.jar")
          manifest-text (str "Manifest-Version: 1.0\r\nMain-Class: hazel.Main\r\n"
                             "Implementation-Title: Hazel\r\nImplementation-Version: 0.1.0\r\n\r\n")
          seen (atom #{})
          runtime (->> (str/split (System/getProperty "java.class.path")
                                 (re-pattern (java.util.regex.Pattern/quote (System/getProperty "path.separator"))))
                       (filter #(str/ends-with? % ".jar")) (sort))]
      (with-open [out (JarOutputStream. (io/output-stream destination))]
        (letfn [(entry! [name stream]
                  (when-not (contains? @seen name)
                    (swap! seen conj name)
                    ;; Clojure selects source when its timestamp is not older
                    ;; than __init.class. Preserve that ordering deterministically.
                    (let [timestamp (+ 315532800000 (if (str/ends-with? name ".class") 2000 0))
                          entry (doto (JarEntry. name) (.setTime timestamp))]
                      (.putNextEntry out entry)
                      (io/copy stream out)
                      (.closeEntry out))))]
          (with-open [in (java.io.ByteArrayInputStream. (.getBytes manifest-text java.nio.charset.StandardCharsets/UTF_8))]
            (entry! "META-INF/MANIFEST.MF" in))
          (doseq [directory ["target/classes" "src" "resources"] [name file] (files-under directory)]
            (with-open [in (io/input-stream file)] (entry! name in)))
          (doseq [path runtime]
            (with-open [jar (JarFile. path)]
              (doseq [entry (sort-by #(.getName %) (enumeration-seq (.entries jar)))
                      :let [name (.getName entry)]
                      :when (and (not (.isDirectory entry))
                                 (not (str/starts-with? name "META-INF/"))
                                 (not= name "module-info.class"))]
                (with-open [in (.getInputStream jar entry)] (entry! name in)))
              ;; Preserve each dependency's license separately even when names collide.
              (doseq [entry (enumeration-seq (.entries jar))
                      :let [name (.getName entry)]
                      :when (and (not (.isDirectory entry))
                                 (re-find #"(?i)(license|notice|copyright)" name))]
                (with-open [in (.getInputStream jar entry)]
                  (entry! (str "META-INF/licenses/" (.getName (io/file path)) "/" name) in)))))))
      (when-not (= source-before (source/fingerprint))
        (throw (ex-info "Source changed while packaging" {})))
      (let [result {:path (.getAbsolutePath destination) :bytes (.length destination)
                    :sha256 (edn/sha256-bytes (Files/readAllBytes (.toPath destination)))
                    :source-fingerprint source-before :runtime-jars (mapv #(.getName (io/file %)) runtime)}]
        (spit "target/build.edn" (pr-str result)) result))))

(defn -main [& _] (println (pr-str (build!))))
