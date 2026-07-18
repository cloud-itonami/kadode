(ns kadode.tests.wasm-test
  "kadode 門出 — WASM component entry tests (ADR-2606112238). 1:1 Clojure port of
  tests/test_wasm.py (pytest → clojure.test). Pure stdlib, NETWORK-FREE.

  The Python __main__ demo runner is intentionally omitted (no behaviour, just printing)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [kadode.wasm.app :as app]))

(deftest test-analyze-export-shape
  (let [out (app/parse-json (app/analyze))]
    (is (= (set (keys out)) #{"routes" "ground_support" "risk_coverage"}))
    ;; G1 in the WASM export: every negotiation-needing scenario routes to a negotiating actor
    (doseq [[sid r] (get out "routes")]
      (when (get r "needs_negotiation")
        (is (contains? #{"labor-union" "lawyer" ":labor-union" ":lawyer"} (get r "actor"))
            (str sid " negotiation routed to " (get r "actor")))))))

(deftest test-datoms-export-is-eavt-edn
  (let [edn (app/datoms 7)]
    (is (and (str/starts-with? (str/triml edn) ";;") (str/includes? edn " 7 :add]")))
    (is (str/includes? edn ":bond/is-transient true"))))

(deftest test-coverage-export-is-markdown
  (let [md (app/coverage)]
    (is (and (str/starts-with? md "# kadode") (str/includes? md "holds for all scenarios")))))

(deftest test-generate-export-renders-resignation
  (let [doc (app/generate "taishoku-todoke" (app/->json {"worker" "山田太郎" "date" "令和8年7月15日"}))]
    (is (and (str/includes? doc "退職届") (str/includes? doc "民法627条")))))

(deftest test-relay-export-refuses-negotiation-scenario
  (let [out (app/parse-json (app/relay "sc.damages-threatened" (app/->json {"worker" "山田太郎"})
                                       "did:plc:worker" "employer-hash"))]
    (is (= (get out "$type") "com.etzhayyim.kadode.escalation"))
    (is (and (= (get out "relayed") false)
             (contains? #{":labor-union" ":lawyer"} (get out "escalateActor"))))))

(deftest test-relay-export-relays-non-negotiation-scenario
  (let [out (app/parse-json (app/relay "sc.permanent-cant-face"
                                       (app/->json {"worker" "山田太郎" "date" "令和8年7月15日"})
                                       "did:plc:worker" "employer-hash"))]
    (is (= (get out "$type") "com.etzhayyim.kadode.resignationRelay"))
    (is (and (= (get out "status") "drafted-unsent") (= (get out "negotiates") false)))))

(deftest test-exports-deterministic
  (is (= (app/analyze) (app/analyze)))
  (is (= (app/datoms 1) (app/datoms 1))))

#?(:clj (defn -main [& _] (run-tests 'kadode.tests.test-wasm)))
