(ns kadode.tests.test-generate
  "kadode 門出 — document-generator + 使者-relay tests (ADR-2606112238). 1:1 Clojure port of
  tests/test_generate.py (pytest → clojure.test). Pure stdlib, network-free.

  Verifies:
    - the 退職届 renders deterministically, states its statutory basis (民法627), and contains
      ONLY \"一身上の都合\" — never a demand / negotiation / settlement figure (G1)
    - missing fields render as explicit blanks, never invented (G2)
    - assert-no-negotiation rejects demand/negotiation language injected into a free-text field
    - build-relay RELAYS a non-negotiating scenario but REFUSES a negotiation-needing one and
      returns the escalation route instead (the action-layer UPL boundary, G1)
    - the relay record is drafted-UNSENT (no-server-key); document is content-addressed

  The Python __main__ demo runner is intentionally omitted (no behaviour, just printing)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [kotoba.lang.text :as str]
            [clojure.java.io :as io]
            [kadode.methods.analyze :as analyze]
            [kadode.methods.cid :as cid]
            [kadode.methods.generate :as generate]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-resignation-graph.kotoba.edn"))

(defn- load-seed [] (analyze/load-file* seed))

(deftest test-taishokutodoke-renders-unilateral-and-traceable
  (let [doc (generate/render "taishoku-todoke" {"worker" "山田太郎" "employer" "株式会社ABC"
                                                "date" "令和8年7月15日"})]
    (is (= doc (generate/render "taishoku-todoke" {"worker" "山田太郎" "employer" "株式会社ABC"
                                                   "date" "令和8年7月15日"}))
        "not deterministic")
    (is (and (str/includes? doc "退職届") (str/includes? doc "一身上の都合")))
    (is (str/includes? doc "民法627条") "missing statutory basis")
    (is (str/includes? doc "承諾を要しません") "must state the employer's consent is not required")
    ;; no negotiation/demand language ever appears in a kadode resignation
    (doseq [bad generate/prohibited-negotiation]
      (is (not (str/includes? doc bad))))))

(deftest test-missing-fields-render-as-blanks
  (let [doc (generate/render "taishoku-todoke" {"worker" "山田太郎"})]
    (is (str/includes? doc "［　　］") "missing fields must render as explicit blanks, never invented")))

(deftest test-assert-no-negotiation-rejects-demands
  (doseq [bad ["示談金を支払えと請求します" "退職金を増額する条件交渉をしたい" "慰謝料を請求"]]
    (is (thrown? #?(:clj Exception :cljs js/Error) (generate/assert-no-negotiation bad))
        (str "negotiation text accepted: " bad)))
  (generate/assert-no-negotiation "一身上の都合により退職します")) ; clean text passes

(deftest test-render-rejects-negotiation-in-note-field
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (generate/render "taishoku-todoke" {"worker" "A" "note" "解決金を請求します"}))
      "render accepted negotiation language in note"))

(deftest test-relay-conveys-non-negotiating-scenario
  (let [{:keys [nodes edges]} (load-seed)
        doc (generate/render "taishoku-todoke" {"worker" "山田太郎" "employer" "株式会社ABC"
                                                "date" "令和8年7月15日"})
        rec (generate/build-relay "sc.permanent-cant-face" doc "did:plc:worker" "employer-hash"
                                  nodes edges "2026-06-11T00:00:00Z")]
    (is (= (get rec "$type") "com.etzhayyim.kadode.resignationRelay"))
    (is (and (= (get rec "relayed") false) (= (get rec "status") "drafted-unsent"))) ; no-server-key
    (is (and (= (get rec "negotiates") false) (= (get rec "role") "messenger-使者")))
    (is (= (get rec "documentCid") (cid/cidv1-raw (cid/->bytes doc))))))

(deftest test-relay-refuses-negotiation-scenario-and-escalates
  (testing "G1 at the action layer: a 使者 must never relay a matter needing negotiation."
    (let [{:keys [nodes edges]} (load-seed)
          doc (generate/render "taishoku-todoke" {"worker" "山田太郎" "employer" "X" "date" "令和8年8月1日"})]
      (doseq [sid ["sc.damages-threatened" "sc.unpaid-wages" "sc.harassment" "sc.yukyu-refused"]]
        (let [rec (generate/build-relay sid doc "did:plc:worker" "employer-hash" nodes edges)]
          (is (= (get rec "$type") "com.etzhayyim.kadode.escalation") (str sid " should escalate"))
          (is (= (get rec "relayed") false))
          (is (contains? #{":labor-union" ":lawyer"} (get rec "escalateActor"))
              (str sid " must escalate to union/lawyer, got " (get rec "escalateActor"))))))))

#?(:clj (defn -main [& _] (run-tests 'kadode.tests.test-generate)))
