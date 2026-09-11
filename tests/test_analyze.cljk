(ns kadode.tests.test-analyze
  "kadode 門出 — analyzer + UPL-boundary tests (ADR-2606112238). 1:1 Clojure port of the PURE
  assertions of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - G1 (THE defining boundary): EVERY negotiation-needing scenario resolves to a NEGOTIATING
      route (union/lawyer) — NEVER to 使者/self; the 使者/self routes are never can-negotiate
    - every scenario reaches a recommended lawful route
    - every employer-risk pattern has at least one countering legal ground
    - edge-primary (N1/G2): ground-support is the integral of incident :supported-by edges,
      and NO stored per-node :bond/* key exists
    - grounds carry a public source URL + a citation

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally DEFERRED here (the datom_emit
  port is a separate unit, mirroring the inochi/rasen precedent). All PURE analyze assertions
  are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [kotoba.lang.text :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [kadode.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-resignation-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 30) (str (count nodes) " nodes / " (count edges) " 縁"))
    (is (>= (count edges) 40) (str (count nodes) " nodes / " (count edges) " 縁"))
    (let [kinds (set (map #(get % ":lx/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":scenario" ":ground" ":document" ":route" ":risk"} kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling 縁: " e))
      (is (contains? nodes (get e ":en/to")) (str "dangling 縁: " e)))))

(deftest test-g1-upl-invariant-holds-for-every-scenario
  (testing "THE defining boundary: negotiation ⇒ union/lawyer, never a 使者/self relay."
    (let [{:keys [nodes edges]} (load-seed)]
      (doseq [[nid n] nodes
              :when (= ":scenario" (get n ":lx/kind"))]
        (let [rec (analyze/recommend-route nid nodes edges)]
          (when (get rec "needs_negotiation")
            (is (contains? analyze/negotiating-actors (get rec "route_actor"))
                (str "G1 VIOLATION: " nid " needs negotiation but routed to "
                     (get rec "route_actor")))
            (is (= true (get rec "can_negotiate")))))))))

(deftest test-messenger-and-self-routes-cannot-negotiate
  (let [{:keys [nodes]} (load-seed)]
    (doseq [n (vals nodes)
            :when (and (= ":route" (get n ":lx/kind"))
                       (contains? #{":worker-self" ":kadode-messenger"} (get n ":route/actor")))]
      (is (= false (get n ":route/can-negotiate"))
          (str (get n ":lx/id") " (使者/self) must not be able to negotiate (G1)")))))

(deftest test-every-scenario-routes
  (let [{:keys [nodes edges]} (load-seed)]
    (doseq [[nid n] nodes
            :when (= ":scenario" (get n ":lx/kind"))]
      (is (get (analyze/recommend-route nid nodes edges) "route") (str nid " has no route")))))

(deftest test-every-risk-has-a-countering-ground
  (let [{:keys [nodes edges]} (load-seed)
        countered (set (for [e edges :when (= ":counters" (get e ":en/kind"))] (get e ":en/to")))]
    (doseq [[nid n] nodes
            :when (= ":risk" (get n ":lx/kind"))]
      (is (contains? countered nid)
          (str "employer risk " nid " has no countering legal ground")))))

(deftest test-edge-primary-ground-support
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        expect (reduce
                (fn [m e]
                  (if (= ":supported-by" (get e ":en/kind"))
                    (update m (get e ":en/from") (fnil + 0.0) (double (get e ":en/weight")))
                    m))
                {} edges)]
    (doseq [[sid v] expect]
      (is (< (Math/abs (- (get-in res ["ground_support" sid]) v)) 1e-9)
          (str sid ": " (get-in res ["ground_support" sid]) " != " v)))
    ;; no stored per-scenario score on any node (edge-primary only)
    (doseq [n (vals nodes)]
      (is (not (some #(str/starts-with? % ":bond/") (keys n)))))))

(deftest test-grounds-have-public-source
  (let [{:keys [nodes]} (load-seed)]
    (doseq [[nid n] nodes
            :when (= ":ground" (get n ":lx/kind"))]
      (is (get n ":ground/citation") (str nid " missing citation"))
      (is (str/starts-with? (str (get n ":ground/url" "")) "http")
          (str nid " missing public url")))))
