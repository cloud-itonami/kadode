(ns kadode.methods.datom-emit-test
  "kadode 門出 — Datom-emit tests (ADR-2606112238), 1:1 port of the deferred datom tests in
  tests/test_analyze.py (test_datom_emit_ground_and_transient + test_determinism)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [kadode.methods.analyze :as analyze]
            [kadode.methods.datom-emit :as datom-emit]))

(def seed "data/seed-resignation-graph.kotoba.edn")

(defn- load-seed [] (datom-emit/load-file* seed))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (and (str/includes? out " 7 :add]") (str/includes? out ":route/can-negotiate")))
    (is (str/includes? out ":upl-bound") "UPL-boundary edges must be in the Datom log")
    (is (str/includes? out ":bond/is-transient true"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]"))))))

(deftest test-determinism
  (let [{n1 :nodes e1 :edges} (load-seed)
        a (datom-emit/emit n1 e1 (analyze/analyze n1 e1) 1)
        {n2 :nodes e2 :edges} (load-seed)
        b (datom-emit/emit n2 e2 (analyze/analyze n2 e2) 1)]
    (is (= a b))))
