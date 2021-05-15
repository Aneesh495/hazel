(ns hazel.main
  "Minimal AOT bootstrap for the executable JAR. Application namespaces load from
  the bundled Clojure source, then use the normal runtime JIT compilation path."
  (:gen-class :name hazel.Main))

(defn -main [& args]
  (require 'hazel.cli)
  (apply (resolve 'hazel.cli/-main) args))
