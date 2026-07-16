(ns filmprodops.advisor
  "FilmProductionAdvisor -- the *contained intelligence node* for the
  ISIC-5911 motion-picture/video/TV-programme-production operations-
  coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: shoot-day/scene/take record logging, shoot-day/location/
  crew scheduling proposals, on-set safety-concern flagging, and
  footage/dailies post-production handoff coordination. CRITICAL: it is
  a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record and NEVER
  a direct actuation -- every proposal's `:effect` is always `:propose`.
  Every output is censored downstream by `filmprodops.governor` before
  anything touches the SSoT.

  This advisor NEVER finalizes an on-set safety-clearance decision
  (stunt-clearance sign-off, minor-performer work-hour-limit override),
  NEVER directly actuates rigging/pyrotechnic/stunt equipment, and NEVER
  finalizes talent compensation/contract or insurance/legal/union
  matters -- those are permanently out of scope for this actor, not
  merely un-implemented. `filmprodops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal for
  exactly this failure mode (a compromised or confused advisor drifting
  into scope it must never touch) and HARD-holds it, regardless of
  confidence or op.

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

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-production-record (propose-production-record _db request)
                   :schedule-production-operation (propose-production-schedule _db request)
                   :flag-onset-safety-concern (propose-onset-safety-concern _db request)
                   :coordinate-post-production-handoff (propose-post-production-handoff _db request)
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
