# 첫 커밋 / GitHub 업로드 전 체크리스트

포트폴리오 repo에 올리기 전에 아래 항목을 확인하세요.

## 1. 올리면 안 되는 것

- [ ] `.cursor/` 폴더가 추적 대상에 없음
- [ ] `.cursor-kit.json` 이 추적 대상에 없음
- [ ] `vendor/` (cursor-workspace-kit) 가 추적 대상에 없음
- [ ] `.gitmodules` 가 추적 대상에 없음
- [ ] `.env`, `application-local.yml` 같은 로컬 설정이 없음
- [ ] API key, DB password, token 같은 비밀값이 코드/README에 없음
- [ ] `build/`, `.gradle/` 같은 빌드 산출물이 없음

확인 명령:

```powershell
git status
git ls-files
```

## 2. 올려야 하는 것

- [ ] `src/`
- [ ] `README.md`
- [ ] `build.gradle`, `settings.gradle`
- [ ] `gradlew`, `gradlew.bat`, `gradle/wrapper/`
- [ ] `.gitignore`
- [ ] `.gitattributes` (선택)
- [ ] `.editorconfig` (선택)

## 3. README 점검

- [ ] 프로젝트 한 줄 소개가 있음
- [ ] 기술 스택(Spring Boot, Ollama, pgvector)이 명시됨
- [ ] 실행 방법이 있음
- [ ] Ollama 모델 pull 안내가 있음
- [ ] 현재 진행 상태(Day 1, Day 2...)가 있음
- [ ] README 내용과 실제 코드가 일치함

## 4. 동작 확인

- [ ] `.\gradlew.bat build` 성공
- [ ] `.\gradlew.bat bootRun` 실행 가능
- [ ] `GET /api/health` 정상
- [ ] Ollama 연결 테스트 API 정상 (모델 설치 후)

## 5. 포트폴리오용 품질

- [ ] 커밋 메시지가 기능 단위로 나뉘어 있음
- [ ] "Cursor가 만들어줌" 수준이 아니라 내 설계/선택이 README에 보임
- [ ] 면접에서 설명 못 하는 코드는 넣지 않음
- [ ] (가능하면) 2분 데모 영상 링크 준비

## 6. GitHub repo 설정

- [ ] repo name: `rag-assistant`
- [ ] Public / Private 선택
- [ ] GitHub에서 README/.gitignore 자동 생성하지 않음
- [ ] Description 작성
- [ ] Topics 추가: `spring-boot`, `rag`, `ollama`, `pgvector`, `java`

Description 예시:

```text
Local document Q&A assistant with Spring Boot, Ollama, and PostgreSQL pgvector.
```

## 7. 첫 커밋 예시

```powershell
git add .
git status
git commit -m "Initial project scaffold for local RAG assistant"
git branch -M main
git remote add origin https://github.com/Hyun-Kim95/rag-assistant.git
git push -u origin main
```

`git add .` 전에 반드시 `git status`로 Cursor 관련 파일이 빠졌는지 확인하세요.

## 8. push 직전 마지막 확인

- [ ] `git ls-files | findstr /i "cursor vendor gitmodules env local"`
- [ ] 위 명령 결과가 비어 있거나, 의도한 파일만 보임
- [ ] remote URL이 본인 repo가 맞음

## 참고

- Cursor를 사용한 것 자체는 문제 없습니다.
- 다만 `.cursor/`, workspace kit, agent 로그는 포트폴리오 repo에 넣지 않는 것이 좋습니다.
- 로컬에서는 Cursor 설정을 계속 사용해도 됩니다. `.gitignore`가 GitHub 업로드만 막아 줍니다.
