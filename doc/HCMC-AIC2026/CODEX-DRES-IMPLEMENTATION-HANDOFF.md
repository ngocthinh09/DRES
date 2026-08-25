# DRES HCMC AIC 2026 – Codex implementation handoff

Tài liệu này là bản ghi nhớ kỹ thuật cho các session Codex tiếp theo làm việc trên
fork DRES tại nhánh `dev`. Nội dung được tổng hợp từ lịch sử commit, source hiện tại,
các template JSON, tài liệu `HD-ChungKet.pdf` trong workspace và các lỗi đã điều tra
trong quá trình phát triển.

Mục tiêu của phần mở rộng là dùng DRES cho hai loại task của HCMC AIC 2026:

- Q&A/VQA: trả lời một câu hỏi bằng đáp án text, video và thời điểm.
- TRAKE (Temporal Retrieval of Aligned Key Events): trả lời nhiều key event theo
  thứ tự bằng một video và danh sách frame ID.

DRES gốc đã có các task temporal và hệ thống chấm điểm chung, nhưng chưa có target,
parser, validator, scorer và form tạo đề phù hợp cho hai format trên. Phần mở rộng
được thiết kế để vẫn dùng model/template JSON chuẩn của DRES, chỉ mapping các ID
identity (`mediaItemId`, collection ID, v.v.) khi import giữa các server.

## Trạng thái repository tại thời điểm viết tài liệu

- Nhánh: `dev`.
- HEAD hiện tại: `3a7be0ac chore(config): configure local DRES storage and cache`.
- Trước khi tạo tài liệu này, working tree không có thay đổi source chưa commit; file
  handoff này hiện là thay đổi tài liệu mới và chưa nằm trong `3a7be0ac`.
- Năm commit trực tiếp của phần mở rộng, theo thứ tự thời gian:

| Commit | Nội dung |
| --- | --- |
| `64b26e76` | Thêm model/API, parser, validator, TRAKE scorer, preset và UI nền cho Q&A/TRAKE. |
| `e16ad1b5` | Hoàn thiện format TRAKE dùng frame ID, chuyển frame sang ms, hỗ trợ nhiều query target và alternative answer, sửa form editor/import. |
| `56a4812c` | Đồng bộ preset Q&A với tên/schema template LSC; bỏ preset AIC trùng; đặt max KIS 1000. |
| `0b090fec` | Đổi FFmpeg preview-video preset từ `slow` sang `veryfast`. |
| `3a7be0ac` | Cấu hình storage, external media, cache bền vững và HTTP port cho deployment local. |

Commit `64b26e76` và `e16ad1b5` là thay đổi nghiệp vụ chính. Hai commit sau điều
chỉnh tính tương thích template và vận hành/caching, không thay đổi luật chấm.

## Bối cảnh và các quyết định thiết kế

### Vì sao không tạo một model task hoàn toàn riêng

Template JSON từ các server DRES khác phải có thể import. Vì vậy target vẫn được
biểu diễn bằng các trường DRES chuẩn (`type`, `target`, `item`, `range`) và chỉ bổ
sung enum/target type cần thiết. ID của media/collection có thể khác giữa server;
quy trình import bên ngoài có trách nhiệm mapping các ID đó. Không nên nhúng logic
mapping identity vào parser hoặc scorer.

### Đơn vị thời gian

Quy ước nội bộ của task và answer temporal là millisecond. `duration` ở cấp template
vẫn là giây theo schema DRES; không được tự đổi `duration: 300` thành `300000` khi
import/export.

TRAKE vẫn nhận đúng format cuộc thi là frame ID, vì đây là format submit công bố trong
`HD-ChungKet.pdf`. Chỉ ở transformer, frame được đổi sang millisecond để validator
và range nội bộ dùng chung một đơn vị.

Các hàm có sẵn được tận dụng:

```kotlin
TemporalPoint.Frame.toMilliseconds(frame, fps)
// (frame / fps * 1000f).toLong()
```

Khi đọc range JSON, `ApiTemporalPoint.toTemporalPoint(fps).toMilliseconds()` cũng
chuẩn hóa `FRAME_NUMBER`, `SECONDS`, `MILLISECONDS` và `TIMECODE` về millisecond.

### Range TRAKE do người ra đề chọn

Không suy ra tolerance từ một frame đúng. Mỗi target TRAKE có `start` và `end` được
người tạo đề nhập thủ công; frame submit sau khi chuyển sang ms phải nằm trong range
đóng `[start, end]`. Điều này cho phép đề dùng range riêng theo yêu cầu thay vì phụ
thuộc vào một sai số frame tự động.

### Một video dùng chung cho TRAKE

Giao diện yêu cầu chọn video một lần ở cấp task. Các target phía dưới chỉ chứa các
range theo thứ tự key event. Khi serialize, mỗi target vẫn được ghi cùng media item
để giữ JSON DRES tương thích; khi import lại, form lấy media item từ target đầu tiên
và hiển thị lại ở selector chung.

## Các commit và thay đổi chi tiết

### `64b26e76` – nền tảng HCMC AIC Q&A và TRAKE

Commit này giải quyết thiếu hụt cốt lõi của DRES gốc.

#### Enum/model/API

Đã thêm hoặc mở rộng:

- `DbTargetOption.TEXT_VIDEO_SEGMENT`: textual answer gắn với video segment.
- `DbTargetOption.TRAKE`: task có các key event temporal theo thứ tự.
- `DbTargetType.TEXT_MEDIA_ITEM_TEMPORAL_RANGE`: gói text + media item + range
  trong một target composite.
- `DbScoreOption.TRAKE`.
- `DbVerdictStatus.PARTIAL` và các enum tương ứng ở API/OpenAPI.
- `ordinal` trên `DbTaskTemplateTarget`, để giữ thứ tự target khi export/import và
  để TRAKE map answer thứ `i` với range thứ `i`.

`TEXT_MEDIA_ITEM_TEMPORAL_RANGE` yêu cầu `text`, `item`, `start` và `end`; các
property constraint của `DbTaskTemplateTarget` đã được cập nhật tương ứng. Phương
thức `toApi()` serialize composite target đủ cả text, item và range.

#### Submission transformer

File chính: `backend/src/main/kotlin/dev/dres/run/transformer/AicTextSubmissionTransformer.kt`.

Client nộp một `ApiClientSubmission` với đúng một answer text thô. Transformer kiểm
tra task ID và điều kiện answer không có media/range sẵn, rồi parse thành các answer
temporal của DRES.

Q&A:

```text
QA-<ANSWER>-<VIDEO_ID>-<TIME_MS>
```

`ANSWER` có thể chứa dấu `-`; regex tách video từ phía sau dựa trên quy ước video ID
có dấu `_`. Thời điểm được dùng làm cả `start` và `end`, vì câu trả lời là một điểm.

TRAKE:

```text
TR-<VIDEO_ID>-<FRAME_ID1>,<FRAME_ID2>,...
```

Transformer lấy metadata `fps` và `durationMs` của video trong collection, đổi từng
frame bằng `TemporalPoint.Frame.toMilliseconds`, rồi tạo một answer temporal cho mỗi
frame. Submission bị reject rõ ràng nếu:

- format không khớp;
- video không tồn tại trong collection;
- FPS không hợp lệ hoặc thiếu;
- duration không hợp lệ;
- frame không parse được, vượt `Int.MAX_VALUE`, hoặc nằm ngoài video.

Transformer được đăng ký trong cả `InteractiveSynchronousEvaluation` và
`InteractiveAsynchronousEvaluation`, sau `SubmissionTaskMatchTransformer` và trước
các filter/validator còn lại.

#### Validator

`QaAnswerSetValidator`:

- yêu cầu đúng một answer temporal;
- media item phải đúng;
- `start == end`;
- thời điểm nằm trong range đóng;
- text khớp answer pattern.

`TrakeAnswerSetValidator`:

- số answer phải đúng số target;
- zip theo thứ tự, không sort lại;
- video, temporal point và `start == end` phải khớp từng range;
- toàn bộ target đúng → `CORRECT`;
- `matched * 2 >= targets.size` → `PARTIAL`;
- còn lại → `WRONG`.

Validator chạy ngay (`deferring = false`), nên submission sai format bị reject ở
transformer còn submission hợp format nhưng không đúng target nhận verdict rõ ràng.

#### Scorer TRAKE

File: `backend/src/main/kotlin/dev/dres/run/score/scorer/TrakeTaskScorer.kt`.

Scorer chọn `CORRECT` đầu tiên; nếu không có full correct thì chọn `PARTIAL` đầu tiên.
Các `WRONG` trước answer được chọn mới bị tính penalty. Công thức base/time của
DRES được giữ lại:

```text
score = max(0,
  endPoints + (maxPoints - endPoints) *
  (1 - elapsedMs / durationMs)
  - wrongBefore * penaltyPerWrongSubmission)
```

Nếu kết quả được chọn là `PARTIAL`, score base chia 2. Mặc định của scorer TRAKE là
`max=100`, `end=50`, `penalty=10`; các key cấu hình dùng prefix `TRAKE.`.

#### Template manager và preset ban đầu

`TemplateManager` lưu target ordinal, lookup media item từ `target.item.mediaItemId`
đối với composite target và chuyển range về millisecond. Composite Q&A không có media
item hợp lệ bị lỗi ngay khi import/lưu thay vì tạo target rỗng.

Commit này ban đầu thêm:

- `backend/src/main/resources/dres-type-presets/70_AIC-Q&A.json`;
- `backend/src/main/resources/dres-type-presets/80_AIC-TRAKE.json`.

Preset Q&A sau đó được thay thế bởi commit `56a4812c`; preset TRAKE vẫn nằm ở file
`80_AIC-TRAKE.json`.

### `e16ad1b5` – hoàn thiện semantics, frame conversion và editor

Commit này xử lý các vấn đề phát hiện khi test template thực tế.

#### Nhiều query target và alternative answer của Q&A

Một task có nhiều query target. Mỗi query target là một nhóm:

```text
(media item, temporal range, correct answer, alternative answers...)
```

Các target cùng media item và cùng range được group trong `AbstractInteractiveTask`
bằng `Triple(itemId, start, end)`. Mỗi group giữ nhiều regex/literal answer pattern;
các group khác nhau là các đáp án đúng độc lập. Text không được phép match answer của
group A nhưng dùng video/range của group B.

Đây là lý do không lưu alternative answer vào một field đặc biệt ngoài JSON: khi
serialize, mỗi alternative trở thành một `TEXT_MEDIA_ITEM_TEMPORAL_RANGE` target
riêng, cùng item/range nhưng khác text. JSON vẫn là schema DRES có thể import từ
server khác.

`qaPattern()` giữ quy ước của DRES:

- text bình thường là literal với `CANON_EQ`;
- `\\...\\` là regex có phân biệt hoa thường;
- `\\...\\i` là regex không phân biệt hoa thường.

#### Sửa lỗi `requires exactly one composite target`

Code cũ gọi `.singleOrNull()` cho Q&A, nên template có nhiều target hoặc alternative
bị báo:

```text
A TEXT_VIDEO_SEGMENT task requires exactly one composite target.
```

Code mới yêu cầu ít nhất một composite target, validate tất cả target có đủ text,
media và range, rồi group thành các query target. Đây là nguyên nhân trực tiếp của
lỗi khi mở template VQA đã lưu trước đó.

#### Sửa lỗi media item bị mất khi mở template

Form cũ serialize/initialize target text và media không thống nhất. Code mới:

- lấy media từ `target.item.mediaItemId` đối với composite Q&A;
- resolve media bằng API nếu JSON chỉ có ID;
- reject target composite không resolve được media;
- khi mở lại, group các entry cùng item/range và khôi phục alternative answers.

Lưu ý identity ID giữa server vẫn phải được mapping trước khi import.

#### UI Q&A

`task-template-editor.component.html` và `task-template-form.builder.ts` hỗ trợ:

- media item nằm trước phần answer;
- ô đầu tiên là `Correct answer`;
- nút `Add alternative text` thêm không giới hạn alternative;
- các alternative sau có nút xóa;
- một task có thể thêm nhiều query target độc lập;
- mỗi query target có range riêng và preview riêng.

Form builder serialize từng answer thành target JSON và deserialize theo group cùng
item/range. Nested Angular form được tách đúng bằng `formArrayName="target"`,
`formGroupName`, và `formArrayName="answers"`; đây là điểm cần giữ khi chỉnh UI để
không tái phát lỗi `DbTaskTemplateTarget.text can't be empty`.

#### UI TRAKE và lỗi mở đồng loạt preview

UI TRAKE có selector video dùng chung ở đầu task, sau đó danh sách range. Mỗi range
chỉ chỉnh start/end, không bắt chọn lại video.

Bug cũ dùng một boolean `showVideo`, nên mở một preview làm tất cả range render
player. Code mới dùng `activeVideoTargetIndex: number | null`:

- click range `i` đặt index `i`;
- click lại cùng range đóng preview;
- template dùng `activeVideoTargetIndex === i`;
- `toggleVideoPlayer()` nhận index và media/range tương ứng.

Đây cũng là lý do không được đổi điều kiện template trở lại thành `*ngIf="showVideo"`.

#### Range và thứ tự TRAKE

`trakeTargetForm()` chỉ tạo controls cho start/end/unit. `trakeMediaItemFormControl()`
resolve video từ target đầu tiên khi import. `serializedTargets()` chèn video chung
vào mọi target trước khi gửi API. Backend lưu `ordinal` để validator dùng đúng thứ
tự đã tạo, không phụ thuộc thứ tự database ngẫu nhiên.

### `56a4812c` – đồng bộ preset Q&A và điểm mặc định

Template JSON chuẩn được import từ server khác sử dụng tên `LSC Question & Answer Text`,
không phải `HCMC AIC Q&A`. Để tránh có hai task type cùng semantics và để import
không bị lệch tên, preset cũ `70_AIC-Q&A.json` đã xóa. Preset
`60_LSC-Q&A-Text.json` được chuyển từ:

```json
"targetOption": "JUDGEMENT"
```

sang:

```json
"targetOption": "TEXT_VIDEO_SEGMENT",
"scoreOption": "KIS",
"configuration": {
  "LIMIT_CORRECT_PER_TEAM.limit": "1",
  "KIS.maxPointsPerTask": "1000.0"
}
```

Điểm tối đa Q&A bằng preset là 1000. Khi chỉ đặt `KIS.maxPointsPerTask`, DRES suy ra
`maxPointsAtTaskEnd = 500` và `penaltyPerWrongSubmission = 100`. Template không có
config KIS vẫn dùng default chung của DRES (`100 / 50 / 10`), vì vậy không được nhầm
default scorer với preset đã chỉnh.

### `0b090fec` – tốc độ render preview

File `backend/src/main/kotlin/dev/dres/mgmt/cache/CacheManager.kt` render preview
video bằng FFmpeg:

```text
-ss <start> -i <input> -to <end>
-c:v libx264 -c:a aac -b:v 2000k
-filter:v scale=<previewVideoMaxSize>:-1
-tune zerolatency -preset veryfast
```

Trước commit là `-preset slow`. Thay đổi này chỉ ảnh hưởng preview video trong cache,
không thay đổi media gốc, target range, frame-to-ms conversion hay scoring. Không
còn literal `slow` trong source hiện tại và bytecode `backend.jar` cũng chứa
`veryfast`.

`veryfast` không phải CPU cap. `libx264` vẫn có thể dùng nhiều thread và
`CacheManager` mặc định chạy `maxRenderingThreads = 2`, nên hai FFmpeg song song có
thể làm CPU gần 100%. `veryfast` thường hoàn thành nhanh hơn nhưng không đảm bảo CPU
thấp hơn tại mọi thời điểm.

### `3a7be0ac` – cấu hình deployment local và cache

`config.json` hiện tại:

```json
{
  "httpPort": 8080,
  "enableSsl": false,
  "dataPath": "/home/thinhvln/storage/dres-data",
  "externalMediaLocation": "/home/thinhvln/storage/external",
  "cache": {
    "cachePath": "/home/thinhvln/storage/dres-cache",
    "maxRenderingThreads": 2,
    "previewVideoMaxSize": 480
  }
}
```

Các path là path riêng của máy development, không nên copy nguyên xi sang server
khác. Cache đã được đưa ra ngoài `/home/thinhvln/dres-run`, vì script build xóa và
giải nén lại thư mục dist. Nhờ vậy các preview đã render không mất sau mỗi lần build.

Nếu muốn giảm CPU, giảm `maxRenderingThreads` xuống `1`; nếu muốn giảm thời gian,
giữ `2` hoặc tăng khi máy đủ lõi. `previewVideoMaxSize` nhỏ hơn giúp render nhanh
hơn nhưng giảm chất lượng preview. Đây là trade-off vận hành, không phải thay đổi
luật thi.

## Luồng chạy end-to-end

### Tạo/import template

1. Chọn task type `LSC Question & Answer Text` hoặc `HCMC AIC TRAKE` trong preset.
2. Chọn collection.
3. Với Q&A, thêm nhiều query target; mỗi target chọn video, answer chính,
   alternative và range.
4. Với TRAKE, chọn video một lần; thêm các range theo thứ tự key event.
5. Export JSON. DRES sẽ ghi temporal point theo đơn vị của form nhưng backend chuẩn
   hóa vào ms khi lưu.
6. Khi import server khác, map collection/media identity trước; giữ nguyên các
   field nghiệp vụ, target type, range, answers và configuration.

### Evaluation và submit

1. Evaluation khởi tạo validator dựa trên `targetOption`.
2. Submission textual đi qua AIC transformer.
3. Transformer tạo temporal answers nội bộ bằng millisecond.
4. Duplicate/limit filters của DRES chạy tiếp.
5. Validator đặt verdict.
6. Scorer KIS hoặc TRAKE tính điểm theo thời gian task và các wrong trước đó.

### TRAKE cụ thể

Với template có `n` ranges và video `V`, submit hợp lệ có dạng:

```text
TR-V-F1,F2,...,Fn
```

`Fi` được đổi thành `floor(Fi / fps * 1000)` theo phép tính Kotlin hiện tại, sau đó
so với range thứ `i`. Không được đổi parser sang nhận ms nếu muốn tương thích format
cuộc thi; nếu cần API nội bộ ms thì chỉ thay đổi sau transformer.

## Các lỗi đã gặp và nguyên nhân

### Media item biến mất khi mở lại VQA

Nguyên nhân là form/serializer cũ không phục hồi composite target item từ
`target.item.mediaItemId`, dẫn đến target lưu thiếu item. Khi chạy evaluation,
`AbstractInteractiveTask` không tìm được đúng composite target và báo lỗi.

Đã xử lý bằng resolve media trong form builder, validate composite target ở
`TemplateManager`, và group/serialize Q&A đúng schema.

### `DbTaskTemplateTarget.text can't be empty`

Nguyên nhân là nested `answers` form không được serialize đúng hoặc target composite
được tạo với answer null. Backend yêu cầu `text` cho `TEXT_MEDIA_ITEM_TEMPORAL_RANGE`.

Đã xử lý bằng answer control bắt buộc, serializer flatten từng answer, và backend
reject thông báo rõ ràng nếu thiếu text/media/range.

### `A TEXT_VIDEO_SEGMENT task requires exactly one composite target`

Nguyên nhân là `.singleOrNull()` trong validator setup, không tương thích một task có
nhiều query target hoặc alternative. Đã chuyển sang danh sách target và group theo
item/range.

### Mở một mắt preview làm mọi range mở

Nguyên nhân là UI dùng boolean toàn cục `showVideo`. Đã thay bằng index của target
đang active như mô tả ở trên.

### Thấy `-preset slow` sau khi chạy `run-dres-dev.sh`

`run-dres-dev.sh` chỉ build/unpack, không tự dừng Java process cũ và cũng không tự
khởi động instance mới. Một process cũ có thể đã nạp class chứa `slow` vào memory,
dù JAR trên ổ đĩa đã được thay bằng bản `veryfast`.

Quy trình đúng:

```bash
# dừng DRES cũ bằng Ctrl+C trong terminal/screen/tmux
bash /home/thinhvln/run-dres-dev.sh
cd /home/thinhvln/dres-run/dres-dist
./bin/backend
```

Có thể kiểm chứng bytecode:

```bash
javap -classpath /home/thinhvln/dres-run/dres-dist/lib/backend.jar \
  -c -p 'dev.dres.mgmt.cache.CacheManager$PreviewVideoFromVideoRequest' \
  | rg -A3 -B2 'String -preset|String (veryfast|slow)'
```

Nếu command FFmpeg đang chạy vẫn là `slow`, đó là instance khác/cũ hoặc server khác
đang được browser truy cập; source hiện tại chỉ có `veryfast`.

### CPU cao khi tạo evaluation

Đây là hành vi dự kiến của preview generation: mỗi segment phải decode, scale và
encode lại bằng x264/aac; mặc định tối đa hai render đồng thời. `veryfast` giảm thời
gian so với `slow` nhưng không phải giới hạn CPU. Cache bền vững và chuẩn bị template
trước giờ thi thường hiệu quả hơn render lúc bắt đầu evaluation.

### Màn hình trắng và MIME JavaScript

DRES static server dùng SPA fallback. Nếu browser yêu cầu một file hash JavaScript cũ
không còn trong bundle, fallback có thể trả `index.html` với MIME `text/html`, gây:

```text
Expected a JavaScript-or-Wasm module script but the server responded with MIME type text/html
```

Đã kiểm chứng bundle hiện tại ở cổng 8080: `index.html` tham chiếu đúng hash hiện tại
và file `main-ANX4OYJ7.js` trả `text/javascript`. Khi lỗi lặp lại, nguyên nhân thường
là browser cache, service worker, reverse proxy hoặc browser đang truy cập instance
khác.

Xử lý:

1. DevTools → Application → Clear site data.
2. Network → Disable cache.
3. Hard reload `Ctrl+Shift+R`.
4. Kiểm tra file `.js` lỗi có đúng hash từ `index.html` hiện tại không.
5. Nếu file đúng hash nhưng vẫn trả HTML, kiểm tra reverse proxy không rewrite
   đường dẫn `.js` về `/index.html`.

## FFmpeg/cache và tối ưu vận hành

`CacheManager` có một executor fixed thread pool theo
`config.cache.maxRenderingThreads`. Preview video được lưu tại `cache.cachePath`.
Template preparation có thể khởi tạo nhiều request preview; nếu cache không có file
thì FFmpeg chạy, nếu đã có thì có thể tái sử dụng.

Khuyến nghị cho ngày thi:

- dùng cache path bền vững, không đặt trong thư mục bị xóa khi deploy;
- chuẩn bị/pre-render các template trước giờ thi;
- kiểm tra quyền đọc media và quyền ghi cache;
- chọn `previewVideoMaxSize` phù hợp;
- dùng `maxRenderingThreads=1` nếu ưu tiên ổn định CPU, `2` nếu ưu tiên thời gian;
- chỉ dùng hardware encoder khi binary FFmpeg và máy có hỗ trợ tương ứng;
- không thay đổi scorer/validator để tối ưu preview.

Preset encoding không ảnh hưởng đáp án, verdict hay điểm vì chỉ áp dụng file preview
được tạo từ media gốc.

## WebSocket khi thi đấu

DRES hiện dùng WebSocket cho cập nhật realtime, không dùng HTTP long polling làm kênh
chính:

- frontend mở `/api/ws/run` trong `frontend/src/app/services/websocket.service.ts`;
- backend đăng ký `ws("ws/run", RunExecutor::accept)` trong `RestApi.kt`;
- server gửi event state/submission/score/viewer tới client đang kết nối;
- sau reconnect, frontend resync state bằng REST để không mất event;
- hint/video countdown có thể được schedule phía client; server không nhất thiết gửi
  tick timer mỗi giây.

Một số màn hình quản trị vẫn có REST GET định kỳ cho dữ liệu phụ, nhưng đó là polling
thông thường, không phải long-polling cho kênh thi đấu.

## Tệp source quan trọng

Backend:

- `backend/src/main/kotlin/dev/dres/run/transformer/AicTextSubmissionTransformer.kt`
- `backend/src/main/kotlin/dev/dres/run/validation/QaAnswerSetValidator.kt`
- `backend/src/main/kotlin/dev/dres/run/validation/TrakeAnswerSetValidator.kt`
- `backend/src/main/kotlin/dev/dres/run/score/scorer/TrakeTaskScorer.kt`
- `backend/src/main/kotlin/dev/dres/data/model/run/AbstractInteractiveTask.kt`
- `backend/src/main/kotlin/dev/dres/data/model/run/InteractiveSynchronousEvaluation.kt`
- `backend/src/main/kotlin/dev/dres/data/model/run/InteractiveAsynchronousEvaluation.kt`
- `backend/src/main/kotlin/dev/dres/mgmt/TemplateManager.kt`
- `backend/src/main/kotlin/dev/dres/mgmt/cache/CacheManager.kt`
- `backend/src/main/kotlin/dev/dres/data/model/media/time/TemporalPoint.kt`
- `backend/src/main/kotlin/dev/dres/data/model/template/task/DbTaskTemplateTarget.kt`
- `backend/src/main/resources/dres-type-presets/60_LSC-Q&A-Text.json`
- `backend/src/main/resources/dres-type-presets/80_AIC-TRAKE.json`

Frontend:

- `frontend/src/app/template/template-builder/task-template-form.builder.ts`
- `frontend/src/app/template/template-builder/components/task-template-editor/task-template-editor.component.ts`
- `frontend/src/app/template/template-builder/components/task-template-editor/task-template-editor.component.html`
- `frontend/src/app/services/websocket.service.ts`
- `frontend/src/app/run/run-admin-view.component.ts`
- `frontend/src/app/viewer/run-viewer.component.ts`

Tài liệu nghiệp vụ chi tiết hơn về format và scoring:

- `doc/HCMC-AIC-QA-TRAKE.md`

## Kiểm thử và build

Các test quan trọng được thêm:

- `backend/src/test/kotlin/dev/dres/run/transformer/AicTextSubmissionTransformerTest.kt`:
  QA answer có dấu `-`, TRAKE frame conversion, metadata lỗi, format lỗi.
- `backend/src/test/kotlin/dev/dres/run/validation/QaAnswerSetValidatorTest.kt`:
  alternative answer, nhiều query target, item/range cùng nhóm.
- `backend/src/test/kotlin/dev/dres/run/score/scorer/TrakeTaskScorerTest.kt`:
  full/partial, ưu tiên full, penalty wrong.

Lệnh test backend:

```bash
cd /home/thinhvln/DRES
./gradlew :backend:test \
  --tests 'dres.run.validation.QaAnswerSetValidatorTest' \
  --tests 'dres.run.transformer.AicTextSubmissionTransformerTest' \
  --tests 'dres.run.score.scorer.TrakeTaskScorerTest'
```

Compile Kotlin:

```bash
./gradlew --console=plain -q :backend:compileKotlin
```

Build package frontend/backend:

```bash
./gradlew --console=plain -q :backend:distTar
```

Khi frontend đã thay đổi đáng kể và cần tạo bundle mới, dùng task frontend phù hợp
hoặc `--rerun-tasks` để tránh dùng artifact cũ. Sau đó phải dừng process DRES cũ và
chạy binary trong dist mới; chỉ build tar không thay đổi code đã nạp trong Java.

Script local hiện tại là `/home/thinhvln/run-dres-dev.sh`. Script xóa
`/home/thinhvln/dres-run`, build `backend:distTar`, giải nén, copy `config.json` và
chmod FFmpeg. Vì cache hiện nằm ngoài dres-run nên việc xóa dist không xóa preview
cache.

## Ví dụ template tối thiểu

Q&A:

```json
{
  "name": "Q&A example",
  "duration": 300,
  "targetOption": "TEXT_VIDEO_SEGMENT",
  "hintOptions": ["TEXT"],
  "submissionOptions": ["NO_DUPLICATES", "LIMIT_CORRECT_PER_TEAM", "TEXTUAL_SUBMISSION"],
  "taskOptions": ["HIDDEN_RESULTS"],
  "scoreOption": "KIS",
  "configuration": {
    "LIMIT_CORRECT_PER_TEAM.limit": "1",
    "KIS.maxPointsPerTask": "1000.0"
  },
  "targets": [
    {
      "type": "TEXT_MEDIA_ITEM_TEMPORAL_RANGE",
      "target": "DOG",
      "item": { "mediaItemId": "<mapped-media-id>" },
      "range": { "start": 740000, "end": 750000 }
    }
  ]
}
```

TRAKE:

```json
{
  "name": "TRAKE example",
  "duration": 300,
  "targetOption": "TRAKE",
  "hintOptions": ["TEXT"],
  "submissionOptions": ["NO_DUPLICATES", "TEXTUAL_SUBMISSION"],
  "taskOptions": ["HIDDEN_RESULTS"],
  "scoreOption": "TRAKE",
  "configuration": { "TRAKE.maxPointsPerTask": "1000.0" },
  "targets": [
    {
      "type": "MEDIA_ITEM_TEMPORAL_RANGE",
      "item": { "mediaItemId": "<mapped-media-id>" },
      "range": { "start": 1000, "end": 2000 }
    },
    {
      "type": "MEDIA_ITEM_TEMPORAL_RANGE",
      "item": { "mediaItemId": "<mapped-media-id>" },
      "range": { "start": 5000, "end": 6500 }
    }
  ]
}
```

## Những điều cần kiểm tra trước khi sửa tiếp

- Không đổi tên `LSC Question & Answer Text` về `HCMC AIC Q&A` nếu mục tiêu là import
  template chuẩn; tên preset và `targetOption` phải giữ đồng bộ.
- Không dùng `.singleOrNull()` cho Q&A composite target; một task có thể có nhiều
  query target và một query target có nhiều alternative.
- Không gán media riêng cho từng TRAKE range ở UI; video dùng chung nhưng JSON vẫn
  lặp item khi serialize.
- Không coi `duration` template là millisecond.
- Không parse TRAKE submit như millisecond; format thi là frame ID.
- Không xóa cache persistent khi build nếu muốn evaluation preparation nhanh.
- Không kết luận source còn `slow` chỉ từ một FFmpeg process cũ; kiểm tra PID, command
  line và JAR mà instance thực tế đang chạy.
- Khi thay frontend, phải rebuild/package lại frontend và restart server; nếu không
  browser có thể nhận index/bundle hash lệch và gặp lỗi MIME `text/html`.

## Việc có thể làm tiếp

Các hướng mở rộng chưa được triển khai trong các commit trên:

1. Thêm test integration import/export cho template có nhiều Q&A group và alternative.
2. Thêm test end-to-end cho TRAKE một video chung với nhiều range và frame conversion.
3. Tách preset encoder, số FFmpeg thread và số renderer thành config rõ ràng nếu cần
   vận hành trên máy nhiều cấu hình.
4. Cân nhắc trả 404 cho asset có extension bị thiếu thay vì SPA fallback để lỗi bundle
   cũ dễ chẩn đoán hơn.
5. Thêm cơ chế version/cache-control cho frontend index khi deploy qua reverse proxy.
6. Đánh giá pre-render template và cache warm-up trước giờ thi thay vì tạo preview
   đồng bộ lúc bắt đầu evaluation.

Các việc này phải giữ nguyên format submit, quy ước millisecond nội bộ, JSON mapping
identity và công thức chấm điểm đã ghi ở trên trừ khi yêu cầu cuộc thi thay đổi.
