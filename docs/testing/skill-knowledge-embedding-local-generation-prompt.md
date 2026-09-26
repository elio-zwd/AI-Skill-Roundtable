# Skill Knowledge 

> ，。
> ：codex/skill-knowledge-embedding
> ：Windows 10 / JDK 17 / Android SDK / Python 3。
> ： GEMINI_API_KEY  gemini-embedding-2， Key。

## Room Schema Guard

The Room v15 schema is already committed from CI. Do not regenerate, edit, reformat, or replace `app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json`. After any Gradle compile, `git diff --exit-code -- app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json` must pass.

## 

1. 。
2. 。
3.  commit。
4.  push。
5.  PR。
6.  main。
7. ：
   - app/src/main/assets/skill_knowledge/manifest.json
   - app/src/main/assets/skill_knowledge/index-v1.bin
8. app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json  CI ，；。
8. 、Kotlin、、Gradle 、 test、。
9. ，， GPT 。

## 1. 

：
git fetch origin
git checkout codex/skill-knowledge-embedding
git pull --ff-only origin codex/skill-knowledge-embedding
git status --short
$head = git rev-parse HEAD
$remote = git rev-parse origin/codex/skill-knowledge-embedding
Write-Output "HEAD=$head"
Write-Output "REMOTE_HEAD=$remote"
if ($head -ne $remote) { throw " HEAD  origin/codex/skill-knowledge-embedding " }
git merge-base --is-ancestor origin/main HEAD
if ($LASTEXITCODE -ne 0) { throw "origin/main  HEAD " }

：
- worktree ；
- HEAD == origin/codex/skill-knowledge-embedding；
- origin/main ancestor PASS；
- ，，。

## 2. Python 

：python -m unittest tools.skill_knowledge.test_generate_index
：PASS。，、、。

## 3. Gemini Key

 Key 。：
if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { "GEMINI_API_KEY=MISSING" } else { "GEMINI_API_KEY=SET" }

 MISSING： FAIL；； Key； Key。

## 4. Gemini Embedding API 

， `GEMINI_API_KEY` ** Key**，； `.env`  `GEMINI_API_KEYS` ，， `GEMINI_API_KEY`。，、 Key 。

：

```powershell
python -c "import os; from tools.skill_knowledge.generate_index import _embed_text; v=_embed_text(os.environ['GEMINI_API_KEY'], 'skill knowledge smoke test', 'gemini-embedding-2', 768); print('EMBEDDING_SMOKE_DIMENSION=' + str(len(v)))"
```

：

- `EMBEDDING_SMOKE_DIMENSION=768`；
-  400/401/403/404， GPT  **HTTP + status +**；；
- ，；
- smoke ，。

## 5. 

：
python tools/skill_knowledge/generate_index.py --repo-root . --model gemini-embedding-2 --dimension 768
python tools/skill_knowledge/generate_index.py --repo-root . --validate-only

：
- manifest.json 
- index-v1.bin 
- validate-only  VALID
- manifest schemaVersion=1
- model=gemini-embedding-2
- vectorDimension=768
- vectorEncoding=float32-le
- contentHash Markdown 
- vector offset 

、 API response、 Key。

## 5. Room v15 Schema 

：
.\gradlew.bat compileDebugKotlin
git diff --exit-code -- app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json

：
- compileDebugKotlin PASS；
- 15.json ；
-  15.json diff， FAIL，。

## 8. 

：git status --short
： 2 ；/。
：FAIL；，； 2 。

## 7. 

 PowerShell ：
$files = @(
  "app/src/main/assets/skill_knowledge/manifest.json",
  "app/src/main/assets/skill_knowledge/index-v1.bin"
)
$files | ForEach-Object {
  Get-Item $_ | Select-Object FullName, Length
  Get-FileHash $_ -Algorithm SHA256 | Select-Object Path, Hash
}

 manifest ：
- skills = 44；
-  44  Skill  1 CORE；
- `richard_feynman`  KNOWLEDGE；
- `zhang_xuefeng`  KNOWLEDGE；
- document / KNOWLEDGE document / chunk / index ；
- index 。

## 9. ZIP

：
$zip = Join-Path $env:TEMP "skill-knowledge-generated-artifacts.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "app/src/main/assets/skill_knowledge/manifest.json","app/src/main/assets/skill_knowledge/index-v1.bin" -DestinationPath $zip
Write-Output $zip

 ZIP ， commit / push。 ZIP  Elio， GPT 。

## 

RESULT: PASS | FAIL
HEAD: <sha>
REMOTE_HEAD_MATCH: PASS | FAIL
MAIN_ANCESTOR: PASS | FAIL
WORKTREE_BEFORE: CLEAN | DIRTY
PYTHON_GENERATOR_TEST: PASS | FAIL (<passed>/<total>, expected 6/6)
GEMINI_KEY_STATE: SET | MISSING
EMBEDDING_SMOKE: PASS | FAIL
EMBEDDING_SMOKE_DIMENSION: <n | NOT RUN>
INDEX_GENERATION: PASS | FAIL
VALIDATE_ONLY: PASS | FAIL
COMPILE_DEBUG_KOTLIN: PASS | FAIL
ROOM_SCHEMA_15: PASS | FAIL
ALLOWED_OUTPUTS_ONLY: PASS | FAIL

GENERATED:
- manifest.json: <bytes> / sha256=<hash>
- index-v1.bin: <bytes> / sha256=<hash>
- 15.json: COMMITTED_UNCHANGED | CHANGED

MANIFEST:
- skills=<n> (expected 44)
- documents=<n>
- knowledgeDocuments=<n>
- chunks=<n>
- vectorDimension=768
- indexBytes=<n>

ZIP:
<absolute path>

FAILURE:
- command:
- file/line:
- key error:
- minimal log:

：PASS ，；FAIL ，，； Key；。