# HCMC AIC 2026: Q&A và TRAKE

Tài liệu này ghi lại phần mở rộng DRES để phục vụ các task HCMC AIC Q&A và TRAKE. Phần mở rộng được thực hiện trên nhánh `dev` và không thay đổi ý nghĩa của các task DRES hiện có.

## Phạm vi và quy ước thời gian

- Target Q&A/TRAKE và temporal answer nội bộ của DRES dùng **mili giây**. Riêng chuỗi submission TRAKE nhận Frame ID và được đổi sang ms trước khi validate.
- `duration` của template DRES vẫn là **giây**. Khi import/export JSON, giá trị `duration` được giữ nguyên; hệ thống không tự đổi `300000` thành `300`.
- `VIDEO_ID` phải là tên media item ở DRES và có dạng `XXX_XXXX` (không có dấu `-`). Collection được suy ra từ target/template, không phải từ chuỗi nộp bài.
- Việc map ID media/collection khi chuyển JSON giữa các DRES server là trách nhiệm của quy trình import bên ngoài. Sau khi các ID đã được map, các trường nghiệp vụ còn lại tương thích với JSON template DRES.

## Hai loại task mới

| Nhu cầu | `targetOption` | `target.type` | `scoreOption` |
| --- | --- | --- | --- |
| Q&A | `TEXT_VIDEO_SEGMENT` | `TEXT_MEDIA_ITEM_TEMPORAL_RANGE` | `KIS` |
| TRAKE | `TRAKE` | `MEDIA_ITEM_TEMPORAL_RANGE` | `TRAKE` |

`TEXT_MEDIA_ITEM_TEMPORAL_RANGE` là composite target: đáp án văn bản, video và range được lưu trong cùng một target. TRAKE dùng một danh sách các `MEDIA_ITEM_TEMPORAL_RANGE`; thứ tự trong danh sách là thứ tự các key event cần trả lời.

Với Q&A, bài nộp được chấm đúng nếu text khớp **một** đáp án (chính hoặc thay thế)
và video/timestamp khớp range của **cùng query target**. Các query target khác nhau
là các phương án đúng độc lập; một bài chỉ cần khớp một trong số đó.

Hai enum target option, target type, score option và verdict `PARTIAL` đều đã có ở database model, REST API và OpenAPI (`doc/oas.json`, `doc/oas-client.json`). Template export sắp target TRAKE theo thứ tự đã tạo (`ordinal`) để giữ đúng mapping vị trí đáp án.

## Định dạng nộp bài

Client nộp một textual answer duy nhất cho một task. Transformer nội bộ chuyển chuỗi đó thành temporal answer trước khi các filter/validator chuẩn của DRES chạy.

### Q&A

```text
QA-<ANSWER>-<VIDEO_ID>-<TIME_MS>
```

Ví dụ:

```text
QA-red-and-blue-001_0002-15320
```

- `<ANSWER>` có thể chứa dấu `-`; parser tách từ phía sau dựa trên `VIDEO_ID` có dấu `_`.
- `<TIME_MS>` được biến thành `start = end`.
- Bài đúng khi chỉ có một answer, text khớp đáp án target, video đúng và timestamp nằm trong range đóng `[start, end]` của target.
- Text đáp án dùng quy ước sẵn có của DRES: text bình thường là literal (không phân biệt dạng Unicode tương đương); giá trị được bọc `\\...\\` là regex, và `\\...\\i` là regex không phân biệt hoa/thường.

### TRAKE

```text
TR-<VIDEO_ID>-<FRAME_ID_1>,<FRAME_ID_2>,...
```

Ví dụ:

```text
TR-001_0002-43,183,596
```

- Số Frame ID phải đúng bằng số target range trong template.
- Frame ID thứ `i` được đổi theo `floor(frameId × 1000 / fps)`, với frame bắt đầu từ `0`, rồi so với target range thứ `i`; cả thứ tự và video đều bắt buộc khớp.
- Mỗi thời điểm sau quy đổi phải nằm trong range đóng `[start, end]` do người tạo đề tự chọn. Không có tolerance được suy ra tự động từ frame.
- Video phải tồn tại trong collection và có `fps`/`durationMs` hợp lệ; nếu không, submission bị từ chối với lỗi rõ ràng.
- Đúng toàn bộ là `CORRECT`; đúng ít nhất một nửa số mốc là `PARTIAL`; các trường hợp còn lại là `WRONG`.

Các chuỗi không đúng format bị từ chối ở tầng submission, thay vì được đánh dấu sai một cách mơ hồ.

## Tạo và import template

Hai preset mới có sẵn tại:

- `backend/src/main/resources/dres-type-presets/70_AIC-Q&A.json`
- `backend/src/main/resources/dres-type-presets/80_AIC-TRAKE.json`

Frontend template builder hỗ trợ:

- Q&A: một task có thể có nhiều **query target**. Mỗi query target gồm một video,
  một range và một **Correct answer** chính; có thể thêm nhiều **Alternative answer**
  cho chính video/range đó. Khi export, mỗi alternative được ghi thành một
  `TEXT_MEDIA_ITEM_TEMPORAL_RANGE` riêng nhưng giữ cùng `item` và `range`, nên JSON
  vẫn là JSON template DRES chuẩn và có thể import lại từ server khác. Khi mở lại,
  frontend tự gom các target có cùng video/range thành một query target.
- TRAKE: chọn một video dùng chung ở cấp task, sau đó tạo nhiều target range theo thứ tự. Khi lưu/export, video chung được ghi vào từng target để giữ JSON DRES tương thích.

Ví dụ Q&A tối thiểu:

```json
{
  "name": "Q&A example",
  "duration": 300,
  "targetOption": "TEXT_VIDEO_SEGMENT",
  "hintOptions": ["TEXT"],
  "submissionOptions": ["NO_DUPLICATES", "LIMIT_CORRECT_PER_TEAM", "TEXTUAL_SUBMISSION"],
  "taskOptions": ["HIDDEN_RESULTS"],
  "scoreOption": "KIS",
  "configuration": { "LIMIT_CORRECT_PER_TEAM.limit": "1" },
  "targets": [{
    "type": "TEXT_MEDIA_ITEM_TEMPORAL_RANGE",
    "target": "DOG",
    "item": { "mediaItemId": "<mapped-media-id>" },
    "range": { "start": 12000, "end": 15000 }
  }]
}
```

Ví dụ TRAKE tối thiểu:

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
    { "type": "MEDIA_ITEM_TEMPORAL_RANGE", "item": { "mediaItemId": "<mapped-media-id>" }, "range": { "start": 1000, "end": 2000 } },
    { "type": "MEDIA_ITEM_TEMPORAL_RANGE", "item": { "mediaItemId": "<mapped-media-id>" }, "range": { "start": 5000, "end": 6500 } }
  ]
}
```

`<mapped-media-id>` chỉ là placeholder; khi import từ server khác phải thay bằng ID sau khi mapping. Các target TRAKE trong một task phải tham chiếu cùng một media item.

## Tính điểm

### KIS / Q&A

Q&A dùng `KIS`. Mặc định khi template không cấu hình score:

- `maxPointsPerTask = 100`
- `maxPointsAtTaskEnd = 50`
- `penaltyPerWrongSubmission = 10`

Nếu JSON cấu hình các khóa `KIS.maxPointsPerTask`, `KIS.maxPointsAtTaskEnd`, hoặc `KIS.penaltyPerWrongSubmission`, các giá trị đó được ưu tiên. Nếu chỉ có `maxPointsPerTask`, hai giá trị còn lại lần lượt được suy ra là một nửa và một phần mười của max point.

Với lời giải đúng đầu tiên, điểm theo thời gian là:

```text
max(0, endPoints + (maxPoints - endPoints) × (1 - elapsedMs / durationMs)
       - wrongBefore × penalty)
```

### TRAKE

TRAKE có scorer riêng và dùng cùng công thức base/time/penalty ở trên. Mặc định là `100 / 50 / 10`; các khóa cấu hình là:

- `TRAKE.maxPointsPerTask`
- `TRAKE.maxPointsAtTaskEnd`
- `TRAKE.penaltyPerWrongSubmission`

Ví dụ JSON với `TRAKE.maxPointsPerTask: "1000.0"` cho max point là 1000; nếu hai khóa còn lại không có, end point và penalty được suy ra thành 500 và 100.

Scorer ưu tiên lời giải `CORRECT` đầu tiên. Nếu đội không có lời giải đúng hoàn toàn nhưng có `PARTIAL`, nó dùng lời giải partial đầu tiên và lấy **50%** số điểm đã tính. Chỉ các lần `WRONG` trước kết quả được chọn mới tạo penalty.

## Luồng xử lý và mã nguồn chính

1. `AicTextSubmissionTransformer` nhận textual submission và parse format QA/TR.
2. Các filter chuẩn của DRES tiếp tục kiểm tra duplicate, giới hạn submission và kiểu answer.
3. `QaAnswerSetValidator` hoặc `TrakeAnswerSetValidator` đặt verdict `CORRECT`, `PARTIAL` hoặc `WRONG` ngay lập tức.
4. Synchronous/asynchronous evaluation chọn `KisTaskScorer` hoặc `TrakeTaskScorer`; cấu hình được lấy theo prefix `KIS.`/`TRAKE.` trong JSON.

Tệp backend chính:

- `backend/src/main/kotlin/dev/dres/run/transformer/AicTextSubmissionTransformer.kt`
- `backend/src/main/kotlin/dev/dres/run/validation/QaAnswerSetValidator.kt`
- `backend/src/main/kotlin/dev/dres/run/validation/TrakeAnswerSetValidator.kt`
- `backend/src/main/kotlin/dev/dres/run/score/scorer/TrakeTaskScorer.kt`
- `backend/src/main/kotlin/dev/dres/data/model/run/AbstractInteractiveTask.kt`
- `backend/src/main/kotlin/dev/dres/mgmt/TemplateManager.kt`

## Kiểm thử đã thêm

- `AicTextSubmissionTransformerTest`: Q&A có answer chứa dấu `-`, TRAKE đổi Frame ID sang ms, metadata video không hợp lệ, và format sai.
- `QaAnswerSetValidatorTest`: alternative text, nhiều query target độc lập, và ràng buộc video/range cùng nhóm.
- `TrakeTaskScorerTest`: điểm partial, full answer ưu tiên hơn partial, và penalty cho lời giải sai.

Đã chạy thành công:

```bash
./gradlew :backend:test \
  --tests 'dres.run.validation.QaAnswerSetValidatorTest' \
  --tests 'dres.run.transformer.AicTextSubmissionTransformerTest' \
  --tests 'dres.run.score.scorer.TrakeTaskScorerTest'
```
