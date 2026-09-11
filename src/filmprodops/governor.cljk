(ns filmprodops.governor
  "FilmProductionGovernor -- the independent compliance layer that earns
  the FilmProductionAdvisor the right to commit. The advisor has no
  notion of whether a production is actually registered and verified,
  whether its own proposed `:effect` secretly claims a direct actuation
  instead of a mere proposal, or whether it has silently drifted into a
  permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  ONLY (shoot-day/scene/take record logging, shoot-day/location/crew
  scheduling proposals, on-set safety-concern flagging, footage/dailies
  post-production handoff coordination, completed-production handoff to
  an SNS/platform distribution channel, and platform content-policy-
  concern flagging -- the same coordination-only posture applies
  whether the production is a conventional film/TV shoot or an
  AI-generated, SNS-native video production). It NEVER performs or
  authorizes:
    - finalizing an on-set safety-clearance decision (stunt-clearance
      sign-off, hazard-clearance sign-off)
    - overriding or finalizing a minor-performer work-hour limit
    - direct actuation of rigging/stunt/pyrotechnic equipment
    - talent compensation or contract finalization
    - insurance-claim or legal/union-grievance adjudication
    - finalizing/executing an actual platform posting or publish action
    - finalizing a content-moderation ruling
    - finalizing a monetization-eligibility determination
    - waiving or bypassing an AI-generated-content disclosure obligation

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Production unverified     -- the target production (or shoot-day)
                                    record must exist AND be
                                    independently confirmed
                                    `:registered?`/`:verified?` in the
                                    store before ANY proposal for it may
                                    commit or even escalate. Never
                                    trusts a proposal's own claim about
                                    the production -- re-derived from
                                    the production's own store record,
                                    the same 'ground truth, not
                                    self-report' discipline every
                                    sibling actor's governor uses.
    2. Effect not :propose       -- every proposal's `:effect` MUST be
                                    `:propose`. Any other effect value
                                    is, by construction, a claim to
                                    directly actuate/commit outside
                                    governance -- HARD block, not merely
                                    low-confidence.
    3. Scope exclusion           -- ANY proposal (regardless of op)
                                    whose op, rationale, summary,
                                    citations or draft value touches the
                                    act of FINALIZING an on-set
                                    safety-clearance decision, overriding
                                    a minor-performer work-hour limit, or
                                    directly actuating rigging/stunt/
                                    pyrotechnic equipment (or talent-
                                    compensation/contract finalization,
                                    or insurance/legal/union
                                    adjudication, or finalizing/executing
                                    an actual platform posting or publish
                                    action, or finalizing a content-
                                    moderation ruling, or finalizing a
                                    monetization-eligibility
                                    determination, or waiving/bypassing
                                    an AI-generated-content disclosure
                                    obligation) is a HARD, PERMANENT
                                    block -- this actor's charter
                                    excludes that territory
                                    structurally, not as a rollout
                                    milestone. Evaluated UNCONDITIONALLY
                                    on every proposal. An op outside the
                                    closed six-op allowlist is the SAME
                                    failure mode (an advisor proposing
                                    something it was never authorized to
                                    propose) and is folded into this
                                    same check.

  IMPORTANT (scope-exclusion phrasing discipline): `scope-excluded-terms`
  below are phrased as the finalization/execution ACTION ('finalize the
  stunt clearance', 'override the minor performer work-hour limit'),
  never as a bare noun ('stunt', 'minor'). A bare noun would collide
  with the advisor's own legitimate DEFAULT rationale/summary text for
  `:flag-onset-safety-concern` (which must be free to say the word
  'stunt' when reporting an observed stunt-risk concern) and cause the
  actor to self-block on its own happy path -- a bug independently
  discovered and fixed by multiple sibling actors in this fleet.
  `governor-test`'s `default-mock-advisor-proposals-never-self-trip`
  guards this regression directly.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op is
  `:flag-onset-safety-concern` or `:flag-platform-content-policy-concern`
  -- ALWAYS escalates to a human, regardless of confidence, regardless
  of how clean the proposal otherwise is. `filmprodops.phase`
  independently agrees: neither op is ever a member of any phase's
  `:auto` set either -- two layers, not one."
  (:require [kotoba.lang.text :as str]
            [filmprodops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-production-record :schedule-production-operation
    :flag-onset-safety-concern :coordinate-post-production-handoff
    :coordinate-social-distribution-handoff :flag-platform-content-policy-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-onset-safety-concern :flag-platform-content-policy-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area. Phrased as the finalization/
  execution ACTION, never a bare noun -- see the namespace docstring's
  'scope-exclusion phrasing discipline' note; a bare noun like 'stunt'
  or 'minor' would legitimately appear in a clean
  `:flag-onset-safety-concern` proposal's own rationale/summary and
  self-trip the block on this actor's own happy path."
  ["finalize the stunt clearance" "finalize stunt clearance" "stunt clearance decision"
   "stunt clearance sign-off" "clear the stunt as safe" "approve the stunt as safe"
   "stunt approval decision" "authorize the stunt" "スタント許可の最終判断" "スタント承認を確定"
   "finalize on-set safety clearance" "finalize the on-set safety clearance"
   "on-set safety clearance decision" "safety clearance sign-off decision" "現場安全許可を確定"
   "override the minor performer work-hour limit" "override minor-performer work-hour limit"
   "minor-performer work-hour override" "minor performer hour-limit override"
   "waive the minor performer hour limit" "finalize the minor performer work-hour override"
   "未成年出演者の労働時間制限を解除" "未成年者労働時間の上限を超えて承認"
   "authorize rigging actuation" "trigger the rigging" "actuate the rigging"
   "trigger pyrotechnic" "fire the pyrotechnic" "operate stunt rigging" "actuate stunt equipment"
   "リギングを作動させる" "火薬を起爆させる"
   "finalize talent compensation" "finalize the talent contract" "finalize talent contract terms"
   "settle the insurance claim" "adjudicate the union grievance" "adjudicate union grievance"
   "finalize the platform posting" "finalize platform publishing" "confirm the platform publish"
   "execute the platform post" "publish the content to the platform" "プラットフォームへの投稿を確定"
   "投稿を確定して公開する" "配信プラットフォームへの公開を確定"
   "finalize the content moderation ruling" "finalize the moderation decision"
   "confirm the moderation approval decision" "コンテンツモデレーションの合否判定を確定"
   "モデレーション判定を確定する"
   "finalize monetization eligibility" "confirm the monetization eligibility decision"
   "finalize the monetization determination" "収益化適格性の判定を確定" "マネタイズ可否を確定する"
   "waive the ai-generated content disclosure requirement" "bypass the ai disclosure label requirement"
   "remove the ai-generated content disclosure obligation" "disable the ai-generated content disclosure requirement"
   ;; NOTE: `text-blob` lower-cases the whole blob before scanning, so
   ;; these Japanese terms use ASCII lower-case "ai" (not "AI") -- an
   ;; upper-case "AI" here would never match and would silently defeat
   ;; the check.
   "ai生成コンテンツの開示表示義務を無効化" "ai生成物の開示表示義務を回避する"])

;; ----------------------------- checks -----------------------------

(defn- production-unverified-violations
  "The target production must exist AND be independently `:registered?`/
  `:verified?` in the store -- never trust the proposal's own
  `:production-id` claim without a store lookup."
  [{:keys [production-id]} st]
  (let [p (store/production st production-id)]
    (when-not (and p (:registered? p) (:verified? p))
      [{:rule :production-unverified
        :detail (str production-id " は未登録または未検証のプロダクション -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches finalizing an on-set safety-clearance
  decision, overriding a minor-performer work-hour limit, directly
  actuating rigging/stunt/pyrotechnic equipment, finalizing talent-
  compensation/insurance/legal/union matters, finalizing/executing an
  actual platform posting or publish action, finalizing a content-
  moderation ruling, finalizing a monetization-eligibility
  determination, or waiving/bypassing an AI-generated-content
  disclosure obligation, regardless of confidence or how clean every
  other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "現場安全許可の最終判断/未成年出演者労働時間制限の解除/リギング・火薬の直接作動/タレント契約や保険・労組の裁定/プラットフォームへの投稿・公開の確定/コンテンツモデレーションの合否判定の確定/収益化適格性の判定の確定/AI生成コンテンツの開示表示義務の無効化・回避に触れる提案は永久に禁止"}])))

(defn check
  "Censors a FilmProductionAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [production-id (or (:production-id proposal) (:production-id request))
        hard (into []
                   (concat (production-unverified-violations {:production-id production-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :production-id (:production-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
