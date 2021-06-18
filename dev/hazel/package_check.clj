(ns hazel.package-check
  "Permanent executable-package regression. Child JVMs start outside the checkout
  so missing resources and source-only success cannot hide packaging defects."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hazel.io.edn :as edn]
            [hazel.math.numeric :as n])
  (:import [java.util.concurrent TimeUnit]
           [java.nio.file Files StandardCopyOption]))

(defn process! [arguments working-directory log-path]
  (let [log-file (.getAbsoluteFile (io/file log-path))
        _ (.mkdirs (.getParentFile log-file))
        process (-> (ProcessBuilder. ^java.util.List (mapv str arguments))
                    (.directory (.getAbsoluteFile (io/file working-directory)))
                    (.redirectErrorStream true) (.redirectOutput log-file) .start)]
    (when-not (.waitFor process 120 TimeUnit/SECONDS)
      (.destroyForcibly process)
      (n/fail "Package subprocess timed out" {:command arguments :log (str log-file)}))
    (let [exit (.exitValue process) output (slurp log-file)]
      (when-not (zero? exit)
        (n/fail "Package subprocess failed" {:exit exit :command arguments :log (str log-file) :output output}))
      {:exit exit :output output :log (str log-file)})))

(defn run! [directory]
  (let [root (.getCanonicalFile (io/file ".")) directory (.getCanonicalFile (io/file directory))
        _ (.mkdirs directory)
        java (str (System/getProperty "java.home") "/bin/java")
        build-command [java "-Dfile.encoding=UTF-8" "-cp" (System/getProperty "java.class.path") "clojure.main" "-m" "hazel.build"]
        _ (process! build-command root (io/file directory "build-first.log"))
        first-build (edn/read-file "target/build.edn")
        jar (io/file "target/hazel.jar")
        copied (io/file directory "hazel.jar")
        _ (Files/copy (.toPath jar) (.toPath copied) (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING]))
        _ (with-open [archive (java.util.jar.JarFile. copied)]
            (doseq [entry ["META-INF/licenses/EPL-1.0.html" "META-INF/licenses/NOTICE.txt"]]
              (when-not (.getJarEntry archive entry)
                (n/fail "Standalone package omits required runtime license notice" {:entry entry}))))
        _ (process! build-command root (io/file directory "build-second.log"))
        second-build (edn/read-file "target/build.edn")
        _ (when-not (= (:sha256 first-build) (:sha256 second-build))
            (n/fail "Fresh-process JAR builds differ" {:first first-build :second second-build}))
        temp (.toFile (Files/createTempDirectory "hazel-package-" (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (let [result (process! [java "-jar" copied "run" (io/file root "examples/certificate.edn") "--out" (io/file temp "result")]
                             temp (io/file directory "jar-run.log"))
            data (edn/read-string (last (str/split-lines (:output result))))
            bundle (:bundle data)
            _ (process! [java "-jar" copied "verify" bundle] temp (io/file directory "jar-verify.log"))
            _ (process! [java "-jar" copied "replay" bundle] temp (io/file directory "jar-replay.log"))]
        {:status :passed :sha256 (:sha256 first-build) :bytes (:bytes first-build)
         :fresh-process-builds 2 :identical-builds? true :ran-outside-checkout? true
         :certificate-verified? true :strict-replay? true})
      (finally (doseq [file (reverse (file-seq temp))] (io/delete-file file))))))

(defn -main [& [directory]]
  (println (pr-str (run! (or directory "out/package-check")))))
