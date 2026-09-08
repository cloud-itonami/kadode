(ns kadode.tests.test-coverage
  "kadode 門出 — coverage-report tests (ADR-2606112238). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [kotoba.lang.text :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [kadode.methods.analyze :as analyze]
            [kadode.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-resignation-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-report-renders
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (and (str/starts-with? md "# kadode")
             (str/includes? md "coverage of all employment situations is bounded")))))

(deftest test-upl-invariant-reported-holding
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "holds for all scenarios") "UPL invariant must report as holding")
    (is (not (str/includes? md "VIOLATED")))))

(deftest test-all-routes-present
  (testing "The full escalation ladder (self → messenger → union → lawyer) must exist."
    (let [{:keys [nodes]} (load-seed)
          actors (set (for [n (vals nodes) :when (= ":route" (get n ":lx/kind"))]
                        (get n ":route/actor")))]
      (is (clojure.set/subset?
           #{":worker-self" ":kadode-messenger" ":labor-union" ":lawyer"} actors)))))

(deftest test-every-scenario-and-risk-covered
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)
        scen (count (filter #(= ":scenario" (get % ":lx/kind")) (vals nodes)))]
    (is (str/includes? md (str scen "/" scen)) "all scenarios should reach a route")))
