#!/usr/bin/env bash
# kadode — clj/bb test suite (ADR-2606160842 py->clj port wave). Auto-wired into the fleet
# green-check; runs all cljc test namespaces via babashka from the repo root.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec kbb -e '(require (quote clojure.test) (quote kadode.methods.test-datom-emit) (quote kadode.tests.test-analyze) (quote kadode.tests.test-coverage) (quote kadode.tests.test-generate) (quote kadode.tests.test-kotoba) (quote kadode.tests.test-wasm))(let [r (apply clojure.test/run-tests (quote [kadode.methods.test-datom-emit kadode.tests.test-analyze kadode.tests.test-coverage kadode.tests.test-generate kadode.tests.test-kotoba kadode.tests.test-wasm]))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
