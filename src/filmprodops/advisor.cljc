(ns filmprodops.advisor
  "FilmProductionAdvisor -- the *contained intelligence node* for the
  ISIC-5911 motion-picture/video/TV-programme-production operations-
  coordination actor.

  It drafts exactly six kinds of back-office proposal from a closed
  allowlist: shoot-day/scene/take record logging, shoot-day/location/
  crew scheduling proposals, on-set safety-concern flagging, footage/
  dailies post-production handoff coordination, completed-production
  handoff to an SNS/platform distribution channel, and platform
  content-policy-concern flagging. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation -- every
  proposal's `:effect` is always `:propose`. Every output is censored
  downstream by `filmprodops.governor` before anything touches the SSoT.

  This actor's production-coordination scope explicitly covers AI-
  generated, SNS-native video content (e.g. a text-to-video/image-to-
  video AI generation service's output destined for a social platform)
  the same way it covers a conventional film/TV production -- see
  README `Scope`. It NEVER finalizes an on-set safety-clearance decision
  (stunt-clearance sign-off, minor-performer work-hour-limit override),
  NEVER directly actuates rigging/pyrotechnic/stunt equipment, NEVER
  finalizes talent compensation/contract or insurance/legal/union
  matters, NEVER finalizes/executes an actual platform posting or
  publish action, NEVER finalizes a content-moderation ruling or
  monetization-eligibility determination, and NEVER waives/bypasses an
  AI-generated-content disclosure obligation -- those are permanently
  out of scope for this actor, not merely un-implemented.
  `filmprodops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised
  or confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :production-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-production-record
  "Draft a shoot-day/scene/take record log entry. Pure logging of what
  was shot (scene/take numbers, running order, coverage) -- never a
  safety, HR, or legal determination."
  [_db {:keys [production-id patch]}]
  {:op         :log-production-record
   :production-id production-id
   :summary    (str production-id " の撮影日/シーン/テイク記録を作成: " (pr-str (keys patch)))
   :rationale  "その日撮影したシーンとテイクの記録のみ。安全判断・人事判断・契約判断は含まない。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence 0.93})

(defn- propose-production-schedule
  "Draft a shoot-day/location/crew scheduling PROPOSAL only (never a
  binding call sheet). Actual schedule finalization is always done by
  the production manager/1st AD."
  [_db {:keys [production-id patch]}]
  {:op         :schedule-production-operation
   :production-id production-id
   :summary    (str production-id " の撮影日・ロケーション・クルー配置の調整案: " (pr-str (keys patch)))
   :rationale  "撮影スケジュールの調整案のみ。確定は制作担当者(プロダクションマネージャー/1stAD)が行う。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence 0.88})

(defn- propose-onset-safety-concern
  "Surface an on-set safety/hazard/talent-welfare concern (stunt risk,
  location hazard, working-condition observation) for HUMAN triage.
  This op ALWAYS escalates in `filmprodops.governor` -- never
  auto-committed at any phase -- regardless of how confident the
  advisor is that the concern is real."
  [_db {:keys [production-id patch]}]
  {:op         :flag-onset-safety-concern
   :production-id production-id
   :summary    (str production-id " の現場安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "現場で観察された安全上の懸念事実の報告のみ。最終的な安全確保の判断は必ず人間(セーフティ責任者)が行う。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence (or (:confidence patch) 0.85)})

(defn- propose-post-production-handoff
  "Draft a footage/dailies handoff coordination to post-production
  (delivery manifest, chain-of-custody note) -- never an editorial or
  final-cut decision."
  [_db {:keys [production-id patch]}]
  {:op         :coordinate-post-production-handoff
   :production-id production-id
   :summary    (str production-id " のラッシュ/デイリー素材のポストプロダクション受け渡し調整: " (pr-str (keys patch)))
   :rationale  "撮影素材のポストプロダクションへの受け渡し調整のみ。編集判断・最終カット判断は含まない。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence 0.90})

(defn- propose-social-distribution-handoff
  "Draft a completed-production-asset handoff coordination to an SNS/
  platform distribution channel (TikTok/YouTube Shorts/Instagram Reels
  etc.) -- delivery manifest, chain-of-custody note. Applies equally to
  a conventional film/TV production and to an AI-generated, SNS-native
  video production. NEVER the actual posting/publish execution, account
  authentication, content-moderation ruling, or monetization-
  eligibility determination -- those remain outside this actor
  entirely."
  [_db {:keys [production-id patch]}]
  {:op         :coordinate-social-distribution-handoff
   :production-id production-id
   :summary    (str production-id " の完成素材をSNS/プラットフォーム配信チャネルへ引き渡す調整案: " (pr-str (keys patch)))
   :rationale  "完成したプロダクション成果物をSNS配信チャネルへ引き渡すための調整のみ。実際の配信実行・モデレーション結果の判断・収益化の可否判断・開示表示の要否判断はこのアクターの範囲外で人間が行う。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence 0.89})

(defn- propose-platform-content-policy-concern
  "Surface a platform content-policy risk (community-guideline risk,
  monetization-eligibility risk, copyright/IP risk, AI-generated-
  content disclosure-labeling obligation) for HUMAN triage. This op
  ALWAYS escalates in `filmprodops.governor` -- never auto-committed at
  any phase -- regardless of how confident the advisor is that the
  concern is real."
  [_db {:keys [production-id patch]}]
  {:op         :flag-platform-content-policy-concern
   :production-id production-id
   :summary    (str production-id " のプラットフォームコンテンツポリシー懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "プラットフォームのコンテンツポリシー上のリスク観察を報告するのみ。モデレーション・収益化・開示表示に関する最終判断は必ず人間が行う。"
   :cites      [production-id]
   :effect     :propose
   :value      (merge {:production-id production-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-production-record (propose-production-record _db request)
                   :schedule-production-operation (propose-production-schedule _db request)
                   :flag-onset-safety-concern (propose-onset-safety-concern _db request)
                   :coordinate-post-production-handoff (propose-post-production-handoff _db request)
                   :coordinate-social-distribution-handoff (propose-social-distribution-handoff _db request)
                   :flag-platform-content-policy-concern (propose-platform-content-policy-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually finalized the stunt clearance decision and overrode the minor performer work-hour limit")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :production-id (:production-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
