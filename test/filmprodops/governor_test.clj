(ns filmprodops.governor-test
  "Pure unit tests of `filmprodops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [filmprodops.advisor :as adv]
            [filmprodops.governor :as gov]
            [filmprodops.store :as store]))

(def production-1 {:production-id "production-1" :title "Neon Harbor" :registered? true :verified? true})
(def production-3 {:production-id "production-3" :title "Skyline Runners" :registered? true :verified? false})

(defn- clean-proposal [op production-id]
  {:op op :production-id production-id :summary "s" :rationale "routine production coordination"
   :cites [production-id] :effect :propose :value {} :confidence 0.85})

(deftest production-unregistered-is-hard
  (testing "no production record at all -> HARD hold"
    (let [s (store/mem-store {"production-1" production-1})
          verdict (gov/check {} nil (clean-proposal :log-production-record "unknown-production") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:production-unverified} (map :rule (:violations verdict)))))))

(deftest production-unverified-is-hard
  (testing "production registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"production-3" production-3})
          verdict (gov/check {} nil (clean-proposal :log-production-record "production-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:production-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"production-1" production-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-production-operation "production-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed six-op allowlist is a scope violation"
    (let [s (store/mem-store {"production-1" production-1})
          verdict (gov/check {} nil (clean-proposal :finalize-stunt-clearance "production-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest stunt-clearance-finalization-is-hard-and-permanent
  (testing "a proposal that finalizes an on-set stunt-clearance decision is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :log-production-record "production-1")
                          :rationale "finalize the stunt clearance for the rooftop chase sequence"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest stunt-approval-decision-content-is-hard
  (testing "a proposal touching a stunt-approval decision (clear the stunt as safe) is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :log-production-record "production-1")
                          :rationale "clear the stunt as safe and proceed with the shot"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest minor-performer-hour-override-content-is-hard
  (testing "a proposal that overrides the minor-performer work-hour limit is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :schedule-production-operation "production-1")
                          :summary "override the minor performer work-hour limit to finish the night shoot")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest rigging-actuation-content-is-hard
  (testing "a proposal that directly authorizes rigging actuation is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :flag-onset-safety-concern "production-1")
                          :value {:decision "authorize rigging actuation for the stunt rig without sign-off"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest talent-compensation-finalization-content-is-hard
  (testing "a proposal that finalizes talent compensation is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :coordinate-post-production-handoff "production-1")
                          :summary "finalize talent compensation for the lead actor's overtime")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest platform-posting-finalization-content-is-hard
  (testing "a proposal that finalizes/executes an actual platform posting is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :coordinate-social-distribution-handoff "production-1")
                          :summary "finalize the platform posting for the trailer cut and go live")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest content-moderation-ruling-finalization-content-is-hard
  (testing "a proposal that finalizes a content-moderation ruling is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :flag-platform-content-policy-concern "production-1")
                          :value {:decision "finalize the content moderation ruling as compliant"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest monetization-eligibility-finalization-content-is-hard
  (testing "a proposal that finalizes monetization eligibility is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :coordinate-social-distribution-handoff "production-1")
                          :summary "finalize monetization eligibility for the channel before handoff")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest ai-disclosure-waiver-content-is-hard
  (testing "a proposal that waives the AI-generated content disclosure requirement is HARD-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          poisoned (assoc (clean-proposal :flag-platform-content-policy-concern "production-1")
                          :value {:decision "waive the ai-generated content disclosure requirement for this upload"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-platform-content-policy-concern-is-not-scope-excluded
  (testing "flagging observed platform content-policy risk (community-guideline/monetization/copyright/AI-disclosure risk) as a CONCERN (not a moderation/monetization/disclosure finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          concern (assoc (clean-proposal :flag-platform-content-policy-concern "production-1")
                         :value {:concern "possible community-guideline risk and undisclosed AI-generated content in the trailer cut"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (platform-policy risk) is exactly what this op exists to surface"))))

(deftest legitimate-safety-concern-is-not-scope-excluded
  (testing "flagging observed stunt-risk/hazard concerns as an ON-SET SAFETY CONCERN (not a safety-clearance finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"production-1" production-1})
          concern (assoc (clean-proposal :flag-onset-safety-concern "production-1")
                         :value {:concern "stunt coordinator flagged elevated risk on the rooftop chase sequence"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (stunt-risk/hazard) is exactly what this op exists to surface"))))

;; ----------------------------------------------------------------------
;; Regression guard for the fleet-wide self-tripping bug pattern: a
;; scope-exclusion term phrased as a bare noun (e.g. "stunt", "minor")
;; would accidentally match inside the mock advisor's own DEFAULT
;; rationale/summary text for a legitimate, allowed proposal, causing
;; the actor to self-block on its own happy path. Every default
;; proposal for every allowed op, against every registered+verified
;; demo production, must clear the scope-exclusion check cleanly.
;; ----------------------------------------------------------------------
(deftest default-mock-advisor-proposals-never-self-trip
  (testing "no default mock-advisor proposal (any allowed op, any demo production) ever trips scope-exclusion"
    (let [s (store/seed-db)]
      (doseq [op [:log-production-record :schedule-production-operation
                  :flag-onset-safety-concern :coordinate-post-production-handoff
                  :coordinate-social-distribution-handoff :flag-platform-content-policy-concern]
              production-id ["production-1" "production-2"]]
        (let [proposal (adv/infer nil {:op op :production-id production-id
                                        :patch {:scene "1A" :take 1
                                                :location "soundstage 2" :date "2026-07-20"
                                                :concern "elevated risk on rooftop chase sequence"
                                                :dailies "day-14" :destination "edit-bay-2"
                                                :platform "youtube-shorts"}})
              verdict (gov/check {} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default proposal for op " op " on " production-id
                   " must never self-trip scope-exclusion: " (:violations verdict)))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default proposal for op " op " must be on the allowlist")))))))
