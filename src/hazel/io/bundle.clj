(ns hazel.io.bundle
  "Content-addressed EDN result objects, published by same-directory atomic rename.
  Hashes detect edits. They do not authenticate an author or prove a calculation."
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [hazel.io.edn :as edn]
            [hazel.math.numeric :as n]
            [hazel.model.network :as net])
  (:import [java.nio.file Files StandardCopyOption AtomicMoveNotSupportedException]
           [java.nio.charset StandardCharsets]))

(defn portable [value]
  (walk/postwalk (fn [x]
                   (cond (= x ##-Inf) {:hazel/nonfinite :negative-infinity}
                         (= x ##Inf) {:hazel/nonfinite :positive-infinity}
                         (and (number? x) (not (n/finite? x))) (n/fail "Cannot persist NaN" {})
                         :else x)) value))

(defn restore [value]
  (walk/postwalk (fn [x]
                   (case x
                     {:hazel/nonfinite :negative-infinity} ##-Inf
                     {:hazel/nonfinite :positive-infinity} ##Inf
                     x)) value))

(defn atomic-write!
  "Only replaces the destination after all bytes are written and fsynced."
  [path text]
  (let [destination (.toPath (.getAbsoluteFile (io/file path)))
        parent (.getParent destination)
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        temp (Files/createTempFile parent ".hazel-" ".tmp" (make-array java.nio.file.attribute.FileAttribute 0))]
    (try
      (with-open [stream (java.io.FileOutputStream. (.toFile temp))]
        ;; Keep the old destination intact until every new byte is written and
        ;; synced. The sibling temporary file stays on the same filesystem.
        (.write stream (.getBytes ^String text StandardCharsets/UTF_8))
        (.sync (.getFD stream)))
      (try
        (Files/move temp destination (into-array StandardCopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))
        (catch AtomicMoveNotSupportedException _
          (n/fail "Filesystem does not support atomic result publication" {:path (str destination)})))
      (str destination)
      (finally (Files/deleteIfExists temp)))))

(defn create!
  [directory model request result]
  (net/compiled! model)
  (let [payload {:format :hazel/bundle-v1 :engine-version "0.1.0"
                 :model (:source model) :model-hash (:hash model)
                 :request (portable request) :result (portable result)}
        digest (edn/sha256 payload)
        object {:digest digest :payload payload}
        path (io/file directory (str digest ".edn"))]
    (atomic-write! path (str (edn/canonical object) "\n"))
    {:path (.getAbsolutePath path) :digest digest} ))

(defn read!
  [path]
  (let [object (edn/read-file path (* 64 1024 1024))
        {:keys [digest payload]} object]
    (net/keys! object #{:digest :payload} :bundle)
    (when-not (= :hazel/bundle-v1 (:format payload)) (n/fail "Unsupported result bundle format" {}))
    (when-not (= digest (edn/sha256 payload)) (n/fail "Bundle content digest mismatch" {}))
    (let [model (net/compile-model (:model payload))]
      (when-not (= (:hash model) (:model-hash payload)) (n/fail "Bundle model hash mismatch" {}))
      {:digest digest :model model :request (restore (:request payload)) :result (restore (:result payload))})))
