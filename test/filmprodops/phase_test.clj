(ns filmprodops.phase-test
  "Unit tests of `filmprodops.phase` rollout logic."
  (:require [clojure.test :refer [deftest is testing]]
            [filmprodops.phase :as phase]))

(def clean-verdict {:hard? false :escalate? false})
(def low-conf-verdict {:hard? false :escalate? true})
(def hard-verdict {:hard? true :escalate? false})

(deftest phase-0-read-only
  (testing "phase 0 allows no writes"
    (doseq [op [:log-production-record :schedule-production-operation
                :flag-onset-safety-concern :coordinate-post-production-handoff]]
      (let [{:keys [disposition]} (phase/gate 0 {:op op} :commit)]
        (is (= :hold disposition)
            (str "phase 0 must hold all ops including " op))))))

(deftest phase-1-production-record-only
  (testing "phase 1 allows only production-record logging, requires approval"
    (let [{:keys [disposition reason]} (phase/gate 1 {:op :log-production-record} :commit)]
      (is (= :escalate disposition))
      (is (= :phase-approval reason)))
    (let [{:keys [disposition]} (phase/gate 1 {:op :schedule-production-operation} :commit)]
      (is (= :hold disposition)))))

(deftest phase-2-adds-coordination-ops
  (testing "phase 2 allows coordination ops, still requires approval"
    (doseq [op [:log-production-record :schedule-production-operation
                :coordinate-post-production-handoff]]
      (let [{:keys [disposition]} (phase/gate 2 {:op op} :commit)]
        (is (= :escalate disposition)
            (str "phase 2 op " op " requires approval"))))))

(deftest phase-3-auto-commits-clean-ops
  (testing "phase 3 auto-commits clean, high-conf non-safety ops"
    (let [{:keys [disposition]} (phase/gate 3 {:op :log-production-record} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :schedule-production-operation} :commit)]
      (is (= :commit disposition)))
    (let [{:keys [disposition]} (phase/gate 3 {:op :coordinate-post-production-handoff} :commit)]
      (is (= :commit disposition)))))

(deftest safety-concern-holds-when-not-enabled
  (testing ":flag-onset-safety-concern holds in phases 0-2 (not yet enabled)"
    (doseq [ph [0 1 2]]
      (let [{:keys [disposition]} (phase/gate ph {:op :flag-onset-safety-concern} :escalate)]
        (is (= :hold disposition)
            (str "phase " ph " has not enabled flag-onset-safety-concern yet"))))))

(deftest safety-concern-escalates-when-enabled
  (testing ":flag-onset-safety-concern ALWAYS escalates when enabled, even if governor says commit"
    (let [{:keys [disposition]} (phase/gate 3 {:op :flag-onset-safety-concern} :commit)]
      (is (= :escalate disposition)
          "phase 3 must escalate safety concerns regardless of governor disposition"))))

(deftest hard-hold-always-wins
  (testing "a governor HARD hold stays HOLD regardless of phase"
    (doseq [ph [0 1 2 3]]
      (let [{:keys [disposition]} (phase/gate ph {:op :log-production-record} :hold)]
        (is (= :hold disposition)
            (str "phase " ph " must respect governor HARD hold"))))))

(deftest verdict->disposition-maps-correctly
  (testing "verdict->disposition correctly translates governor verdict to base disposition"
    (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
    (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
    (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false})))))
