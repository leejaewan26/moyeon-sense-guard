# 감각 가드 (Sense Guard)

척수장애처럼 감각이 없는 부위를 위해 열선·실내 온도에 **내 몸 기준 상한**을 두고, 주행 중엔 풀리지 않게 지키는 Pleos Connect 차량 앱입니다.

현대 기본 열선 자동 하강은 시간 기준이고 누구나 다시 올릴 수 있습니다. 감각 가드는 이렇게 다릅니다.

| | 기본 열선 하강 | 감각 가드 |
|---|---|---|
| 기준 | 시간 (누구나 같음) | 내 몸 (최대 단계, 자동 하강 시간, 실내 온도 상한) |
| 누가 풀 수 있나 | 누구나 언제든 | 주행 중(기어 P 아님)엔 못 풂, P단에서 본인만 "이번 주행만 풀기" |

앱이 켜져 있는 동안 지킵니다(앱이 꺼지면 지키지 못합니다).

## 화면

| 화면 | 하는 일 |
|---|---|
| 가드 (대시보드) | 지금 켜진 규칙 카드, 좌석별 열선 값, 자동 하강 남은 시간, 최근 깎은 기록, 기어 상태, "이번 주행만 풀기"(P단에서만), 하차 복원, Vehicle SDK 호출 기록 |
| 규칙 프로필 | 적용 좌석, 열선 최대 단계, 자동 하강 분, 실내 온도 상한, 알림 방식(소리 / 빛·글자). 기기 안(SharedPreferences)에만 저장, 서버 없음. 주행 중엔 바꿀 수 없음 |
| 나란히 시연 | 왼쪽 '기본 차'(3단 30분 유지) / 오른쪽 '가드'(2단 상한에 깎이고 N분 뒤 자동 하강). 1·30·60배속 |
| 기사 화면 (목업) | 손님 소통 카드("손님 설정 · 최고 26°C"), 휠체어 고정 체크 기록("기사가 체크함"), 체크 전 D 진입 알림 |

## 동작 규칙

- 상한을 넘는 값이 들어오면 되돌리지 않고 **상한까지 깎습니다**(clamp). 화면에 이유를 띄웁니다: "내 설정 · 열선 최대 2단".
- 우리가 쓴 값의 메아리 콜백은 거릅니다.
- 같은 항목을 **60초에 3번** 깎으면 그 항목 가드를 멈추고 화면에 알립니다("다시 켜기" 버튼).
- 상한은 프로필의 자리에만 강제(운전자 = 운전석, 승객 = 그 좌석). 다른 자리는 제안만.
- 실내 온도 상한은 승객 자기 존(조수석)만 강제. 운전자 프로필이면 제안만.
- 내 자리 열선이 켜지면 N분마다 한 단씩 자동 하강.
- "이번 주행만 풀기"는 기어 P에서만. 다음에 다시 P로 돌아오면 가드가 다시 켜집니다.
- 하차 복원은 최선 노력: 지금 값이 가드가 마지막으로 쓴 값과 같을 때만 원래 값으로 되돌립니다.

판정 엔진은 안드로이드 의존 없는 순수 Kotlin(`app/src/main/java/com/moyeon/senseguard/engine/`)이고 JUnit 테스트가 있습니다.

## 실행 방법

```sh
# 설치 (에뮬레이터가 켜져 있을 때)
adb install -r apk/모연_감각가드_release.apk
adb shell am start -n com.moyeon.senseguard/.MainActivity
```

### 시연 시나리오 재생

에뮬레이터 VHAL 패널을 만지지 않아도 30초 시연을 재현합니다.

1. 왼쪽 탭 **나란히 시연** → **시연 시나리오 재생** (기본 30배속, 약 33초). 대시보드의 같은 버튼도 됩니다.
2. 순서: 기어 D → 운전석 3단 요청 → 2단으로 깎임 → 조수석 3단(제안만) → 10분 뒤 자동 하강 → 주행 중 다시 3단 → 깎임 → 기어 P → "이번 주행만 풀기" → 3단 허용.
3. 재생 중에는 상단에 **"시나리오 재생 중 · 가상 신호 (실제 신호 아님)"** 이 깜빡입니다. 기어·열선 요청은 가상 신호이고, 가드가 깎는 쓰기는 실제 Vehicle SDK(`setHvacSeatTemperature`)로 호출합니다.

명령으로 바로 띄우기:

```sh
adb shell am start -n com.moyeon.senseguard/.MainActivity --es tab DEMO --ez scenario true
# tab: DASHBOARD | PROFILE | DEMO | DRIVER
```

### 빌드

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home   # JDK 17
sh ./gradlew test assembleRelease
```

- `local.properties` 에 `sdk.dir=<Android SDK 경로>` 가 필요합니다(저장소 제외).
- release 서명 정보는 저장소 밖 `../keystore/keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) 에서 읽습니다. 파일이 없으면 서명 없이 빌드됩니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | `Pleos_Connect_v2.1.0_API_34` (Android 14, API 34) |
| 화면 | 2560x1440 (`adb shell wm size`), 밀도 160 (`adb shell wm density`), 가로 |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` (nexus-playground maven) |
| 빌드 | Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, Jetpack Compose, compileSdk/targetSdk 35, minSdk 23, JDK 17 |
| 소리 알림 | Android `ToneGenerator` (TextToSpeech SDK 미사용) |

### 쓰는 권한 (AndroidManifest)

| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_SEATS` | 열선 읽기·구독 |
| `pleos.car.permission.CONTROL_CAR_SEATS` | 열선 쓰기 |
| `pleos.car.permission.CAR_CLIMATE` | 실내 설정 온도 읽기·구독 |
| `pleos.car.permission.CONTROL_CAR_CLIMATE` | 실내 설정 온도 쓰기 |
| `pleos.car.permission.CAR_DRIVE`, `CAR_POWERTRAIN` | 기어 상태 읽기 |

## APK 위치

`apk/모연_감각가드_release.apk` (release, 서명됨, minify 끔)

## 사용한 Pleos SDK API

| 기능 | API (Vehicle SDK 2.3.2) |
|---|---|
| SDK 연결·해제 | `Vehicle(context).initialize()`, `Vehicle.release()` |
| 기어 상태 읽기 | `getGear().getCurrentGearSelectionState(onComplete, onFailed)` |
| 기어 변화 구독 | `getGear().registerGearSelection(GearSelectionListener)` / `unregisterGearSelection` |
| 열선 범위 확인 | `getSeat().checkSeatTemperatureCapabilities(onComplete, onFailed)` |
| 열선 값 읽기 | `getSeat().getHvacSeatTemperature(SeatArea, onComplete, onFailed)` |
| 열선 변화 구독 | `getSeat().registerHvacSeatTemperature(SeatArea, HvacSeatTemperatureListener)` / `unregisterHvacSeatTemperature` |
| 열선 깎기·자동 하강·복원 | `getSeat().setHvacSeatTemperature(SeatArea, Int, onComplete, onFailed)` |
| 실내 설정 온도 읽기 | `getHVAC().getHvacTemperature(SeatArea, TemperatureUnit.CELSIUS, onComplete, onFailed)` |
| 실내 설정 온도 구독 | `getHVAC().registerHvacTemperature(SeatArea, HvacTemperatureListener)` / `unregisterHvacTemperature` |
| 실내 온도 깎기 | `getHVAC().setHvacTemperature(SeatArea, Float, TemperatureUnit.CELSIUS, onComplete, onFailed)` |

좌석 매핑: 운전석 `ROW_1_LEFT`, 조수석 `ROW_1_RIGHT`, 2열 `ROW_2_LEFT` / `ROW_2_RIGHT`.

## 에뮬레이터에서 확인한 것 / 한계

- 됨: `initialize`, 기어 읽기(GEAR_PARK), 열선 범위(앞좌석 -3~3, 2열 0~3), 열선 읽기·구독(초기값 0), `setHvacSeatTemperature` 의 onComplete 성공 콜백.
- 확인 안 됨: `setHvacSeatTemperature` 성공 콜백 뒤에도 같은 좌석을 다시 읽으면 0이고 변화 콜백도 오지 않았습니다. 에뮬레이터가 값을 실제로 바꾸지 않는 것으로 보입니다(실차 미확인).
- 안 됨: 실내 설정 온도(`HVAC_TEMPERATURE_SET`) 읽기·구독은 `PropertyNotAvailableException` 을 냅니다(공조 꺼짐 상태로 추정). 앱은 죽지 않고 화면의 SDK 호출 기록에 오류를 그대로 남깁니다.
- 콜백에 '누가 바꿨는지'가 없어서, 본인이 올린 것과 다른 사람이 올린 것을 구분하지 못합니다. 메아리 판별은 "우리가 쓴 값과 같은 값이 처음 들어오면 메아리"로 합니다.
- 앞좌석 열선의 음수 값은 통풍 단계로 보고 "통풍 n"으로 표시합니다.
- 기사 화면은 목업입니다(배차 연동 없음). 이 기기 프로필을 손님 카드로 보여 줍니다.
- "본인만 풀기"의 본인 확인은 없습니다(P단에서 화면 버튼).
