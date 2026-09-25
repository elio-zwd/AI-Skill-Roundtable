# Skill Knowledge + Gemini Embedding 2 

> ： /  /  /  /  API 。
> **。**

## 

- ：`codex/skill-knowledge-embedding`
- Draft PR：#77
- ：Windows 10 / JDK 17 / Android SDK / Python 3 / adb
- Gemini Key： GEMINI Key ； Key

## 

1. 。
2. 。
3.  commit。
4.  push。
5.  merge / close / ready-for-review PR。
6.  main。
7. /， FAIL 。
8. ，；。

## A. /

：
- `git fetch origin`
- `git checkout codex/skill-knowledge-embedding`
- `git pull --ff-only origin codex/skill-knowledge-embedding`
- `git status --short`
- `git rev-parse HEAD`
- `git merge-base --is-ancestor origin/main HEAD`

：
- worktree CLEAN
- main ancestor PASS
- PR #77 Head 

：
- `app/src/main/assets/skill_knowledge/manifest.json`
- `app/src/main/assets/skill_knowledge/index-v1.bin`
- `app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json`

。

## B. /

：
- `python -m unittest tools.skill_knowledge.test_generate_index`
- `python tools/skill_knowledge/generate_index.py --repo-root . --validate-only`

：PASS。

：
- manifest schemaVersion=1
- model=`gemini-embedding-2`
- vectorDimension=768
- vectorEncoding=`float32-le`
- Catalog `hasAsset=true` Skill manifest entry
- `SKILL.md` CORE / retrievalEligible=false
- `references/**` / `research/**` / `examples/**` KNOWLEDGE
- README  SUPPORTING
- contentHash Markdown 
- index offset 

## C. JVM + 

：
- `.\gradlew.bat compileDebugKotlin`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebugAndroidTest`
- `.\gradlew.bat lintDebug assembleDebug`

：。

FAIL ：
- task
- /
- 
-  20-40 

## D. 

：
- `GeminiEmbeddingTransportTest`
- `SkillKnowledgeAssetRepositoryTest`
- `SkillKnowledgeRetrieverTest`
- `SkillKnowledgeContextFormatterTest`
- `SkillKnowledgeArchitectureTest`
- `ExecutionContextBuilderTest`
- `ExecutionRunCoordinatorTest`
- `ContextConfirmationUiStateTest`
- `RoundtableConversationPolicyTest`

：
-  767 / 769 
- ownerSkillId 
- Top12 → 8；  2；<=9000 
- Embedding  Unavailable，
- 24k 
- Top1 Summary Broker 
- SearchMode OFF ，AUTO/ON 
- cross-skill  confirmed context

## E. Room v14→v15

/：
- `RoundtableDatabaseMigrationTest`
- `MaterialContextRepositoryTest`
- `SkillKnowledgeAssetContractTest`

：
- 14→15  schema 
- 
- `skill_knowledge_usage_snapshots` 
- `(runId, sourceSkillId, documentId)` 
- listRunContextUsage  SKILL_KNOWLEDGE
- Material / Personal Context 
- issue purge  FK 
- recovery / backup 

## F. Compose / UI

：
- `ResourcesScreenTest`
- `ContextConfirmationDialogTest`

 UI：
1. 【】 → Skill 。
2. Skill 。
3. ：、、 Markdown 。
4. ：。
5. ： → 。
6. ： → 。
7. 。
8. Skill ：
   - ；
   - ；
   - ；
   - ；
   - Embedding 。

## G. 

：
- ， Skill 。
- “”。
- 。
- App /。

：
- 
- `usage snapshot` title/path/content/hash 

## H.  Gemini Embedding 2

 Key ，。

：
- model=`gemini-embedding-2`
- query=`task: search result | query: ...`
- output dimensionality=768

：
- HTTP 
- vector size=768
- Key

 Key /， NOT RUN，。

## I. 

：
1. Richard Feynman： / 
2. Charlie Munger： / 
3. Duan Yongping： / 

：
- Top hits document path
- heading
- score
- skillId
- Context Pack 

PASS ：
- 
- 
- 
- Context Pack <=9000

## J. 

：Top 1 `RoundtableViewModel`  `ExecutionRunCoordinator`。

：
- `SKILL.md` Role Core
- 
-  Retriever 
- Embedding Role Core 
-  Summary Broker 

：
- 2 Skill 
- A query  B 
- B query  A 
- hits ownerSkillId
-  confirmed context

## K. Secret / diff / 

：
- `pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory`
- `git diff --check`
- `git status --short`
- `git log -10 --oneline`

：
- Secret PASS
- diff PASS
- worktree CLEAN
- 

## 

：

RESULT: PASS | FAIL
HEAD: <sha>
MAIN_ANCESTOR: PASS | FAIL
WORKTREE: CLEAN | DIRTY
GENERATED_ASSETS: PASS | FAIL
PYTHON: PASS | FAIL
COMPILE: PASS | FAIL
JVM: PASS | FAIL
LINT_APK: PASS | FAIL
ROOM_MIGRATION: PASS | FAIL | NOT RUN
REPOSITORY: PASS | FAIL | NOT RUN
ASSET_CONTRACT: PASS | FAIL | NOT RUN
UI_TESTS: PASS | FAIL | NOT RUN
UPGRADE_INSTALL: PASS | FAIL | NOT RUN
REAL_EMBEDDING_API: PASS | FAIL | NOT RUN
RETRIEVAL_SMOKE: PASS | FAIL | NOT RUN
TOP1_EXECUTION_PARITY: PASS | FAIL | NOT RUN
MULTI_ROLE_ISOLATION: PASS | FAIL | NOT RUN
SECRET_DIFF: PASS | FAIL

FAILURES:
- command:
- file/line:
- key error:
- minimal log:

RETRIEVAL_EVIDENCE:
- Feynman: <path#heading / score>
- Munger: <path#heading / score>
- Duan: <path#heading / score>

：
- PASS ；
- FAIL ，；
- NOT RUN 。