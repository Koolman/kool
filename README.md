# 오가는 길 · Galaxy Watch

승인한 시안을 바탕으로 구현한 Wear OS 네이티브 초기 앱과 Android 휴대폰 설정 앱입니다. 웹앱을 워치 브라우저로 띄우는 구조가 아닙니다.

## 구현

- 실행 시 한 번 위치 확인, 집/회사 1.5km 이내·정확도 조건에 따라 출퇴근 판별. 애매하면 직접 선택
- 위치 백그라운드 수집 없음. 타일은 앱 실행 전용
- 출근: 5001-1A(29275) 2대, 마을버스 30(56342) 1대 → 신논현·마곡나루 급행 시각
- 퇴근: 마곡나루·신논현 급행 시각 → 5001-1B(23641), 3칸 중 API가 실제 제공하는 최대 2대 표시
- 동일 열차 두 역 시각, 이전/다음 선택, Refresh로 현재 시각 기준 재선택 및 버스 조회
- 하단 곡선 버튼과 곡선 Refresh 글자, 클래식 숫자 서체, 주황/연두 버스
- 퇴근 버스 남은 분 아래 실제 예상 도착 시각. 시간 경과에 따라 카운트다운, 90초 지난 정보 숨김
- 빠른하차 출근 2-1·4-4 / 퇴근 1-1·4-4
- 휴대폰 앱: API 키·위치·시간표·공휴일 설정, Wear Data Layer 전송
- 시연 모드와 실제 데이터 분리, 기본은 실제 모드(정보 미등록은 —)

## 현재 한계

실기기 검증, 실제 키를 사용한 버스 API 호출/노선 ID 검증, 최신 실제 지하철 시각표 확보, 퇴근 3번째 버스 제공 출처 연결은 미완료입니다. 개인 시험용 debug APK이며 Play 스토어에 게시하지 않았습니다. 시안의 롤렉스 전용 폰트를 사용한 것은 아니며, SIL OFL의 Liberation Serif Bold로 가까운 느낌을 구현했습니다.

[휴대폰만으로 설치·키 발급·설정하기](docs/설치안내.md)

## 소스 빌드

JDK 17, Android SDK 35, Build Tools 35.0.0이 필요합니다. `local.properties`에 SDK 경로를 지정하거나 ANDROID_HOME을 설정합니다.

```sh
./scripts/prepare-dev-key.sh
./gradlew :core:domainChecks :wear:testDebugUnitTest :wear:assembleDebug :phone:assembleDebug
```

생성 APK는 wear/build/outputs/apk/debug/ 및 phone/build/outputs/apk/debug/에 있습니다. 두 모듈은 같은 applicationId와 같은 개인 개발 서명을 사용해야 Wear Data Layer가 연결됩니다. 새 서명으로 다시 빌드하면 이전 APK와 업데이트 호환되지 않으므로 자신의 개발 키를 보관하세요.

서체 라이선스는 docs/FONT-LICENSE.txt에 있습니다. API 키는 저장소에 올리지 않습니다. 앱을 빌드할 때만 쓰려면 루트의 무시된 `.env`에 `BUS_API_KEY=발급받은키`를 넣으세요. 공개 APK에는 키가 포함되지 않으며, 휴대폰 앱에 키를 입력한 뒤 워치로 보내면 됩니다. 집 위치·런타임 설정은 APK에 포함하지 않습니다. 앱 내부 저장소와 Google Wear Data Layer를 이용하며 별도 서버로 보내지 않습니다. SDK 다운로드와 개발용 개인 서명은 tools/에 두고 소스 배포에서 제외합니다.

## 0.1.2

공개용 APK에서 API 키를 제외하고, 휴대폰 설정 화면에서 키를 입력하도록 했습니다. API 키와 개인 설정을 공개 저장소에 올리지 마세요. HTTP 장애·인증 오류·호출 한도 구분을 지원합니다. API 확인 시 이 실행 환경의 연결 경로에서 HTTP 503 upstream connection timeout이 반환되어 실제 도착 응답과 노선 ID는 검증하지 못했습니다.
