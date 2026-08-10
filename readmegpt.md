# TOOLQUEUE - Kế hoạch phát triển Queue Orchestrator

## 1. Vai trò của project

`toolqueue` là bộ điều phối workflow bất đồng bộ, không phải nơi trực tiếp chạy AI hoặc FFmpeg. Trách nhiệm chính:

- Nhận command bắt đầu/cancel/retry pipeline từ `tool`.
- Điều phối thứ tự các stage và dependency giữa chúng.
- Gửi command cho `media-worker` và nhận event kết quả.
- Quản lý retry, timeout, dead-letter queue, concurrency và backpressure.
- Duy trì state machine có thể khôi phục sau restart.
- Phát progress/event chuẩn để Backend và hệ thống quan sát sử dụng.

Không đặt controller nghiệp vụ người dùng, không lưu access token, không nhận binary video/audio, không chạy lệnh shell/FFmpeg trong project này.

## 2. Pipeline mục tiêu

```text
INGEST
  -> PROBE_MEDIA
  -> EXTRACT_AUDIO
  -> DETECT_SCENES + DIARIZE (có thể song song)
  -> TRANSCRIBE
  -> TRANSLATE
  -> SEGMENT_AND_ALIGN
  -> SYNTHESIZE_SPEECH
  -> MIX_AUDIO
  -> GENERATE_SUBTITLE
  -> RENDER_VIDEO
  -> QUALITY_CHECK
  -> COMPLETE
```

MVP có thể gộp `PROBE_MEDIA`, `EXTRACT_AUDIO`, `TRANSCRIBE`, `TRANSLATE`, `SYNTHESIZE`, `RENDER` trong một pipeline worker, nhưng event và state vẫn phải tách theo stage để sau này scale độc lập.

### Ý nghĩa “khớp” theo từng cấp

1. **Timing sync (MVP):** word timestamp, segment start/end, co giãn tốc độ TTS trong giới hạn và chèn khoảng lặng.
2. **Scene/action aware:** tránh cắt câu qua scene cut, dùng OCR/scene/action metadata để dịch đúng ngữ cảnh.
3. **Lip sync nâng cao:** model đồng bộ môi sau khi dubbing; cần GPU, consent, đánh dấu nội dung AI và không nên là điều kiện của MVP.

## 3. RabbitMQ topology đề xuất

### Exchanges

- `media.commands` - direct/topic exchange nhận command.
- `media.events` - topic exchange phát event.
- `media.dlx` - dead-letter exchange.

### Routing keys v1

- `pipeline.requested`
- `pipeline.cancel.requested`
- `stage.execute.{stageName}`
- `stage.cancel.{stageName}`
- `stage.started.{stageName}`
- `stage.progress.{stageName}`
- `stage.completed.{stageName}`
- `stage.failed.{stageName}`
- `pipeline.completed`
- `pipeline.failed`
- `pipeline.cancelled`

### Queue nhóm ban đầu

- `orchestrator.pipeline.requests.v1`
- `orchestrator.stage.events.v1`
- `worker.cpu.commands.v1`
- `worker.gpu.commands.v1`
- `worker.render.commands.v1`
- `orchestrator.dead.v1`

Queue name có version; event name dùng stable business meaning. Không tạo queue động theo từng job.

## 4. Message envelope chuẩn

```json
{
  "eventId": "uuid",
  "eventType": "media.stage.completed",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",
  "correlationId": "uuid",
  "causationId": "uuid",
  "projectId": "uuid",
  "jobId": "uuid",
  "attempt": 1,
  "payload": {}
}
```

Quy tắc:

- Message phải nhỏ; chỉ chứa metadata và object key, không chứa media hoặc transcript rất lớn.
- Consumer deduplicate theo `eventId`; command deduplicate theo `jobId + stage + attempt`.
- `correlationId` đi xuyên toàn pipeline.
- Schema phải validate trước khi xử lý; event không hợp lệ đi quarantine/DLQ.
- Không retry vô hạn. Retryable và non-retryable error phải tách rõ.

## 5. State machine

Mỗi job có một `workflow_instance`; mỗi stage có `PENDING`, `DISPATCHED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `SKIPPED`.

Quy tắc chuyển trạng thái:

- Chỉ dispatch stage khi mọi dependency bắt buộc đã `SUCCEEDED`.
- Event cũ hoặc trùng không được làm lùi trạng thái.
- `FAILED` chỉ retry nếu error được đánh dấu `retryable` và chưa vượt policy.
- Cancel là cooperative: ngừng dispatch stage mới, gửi cancel cho stage đang chạy, sau timeout chuyển terminal theo policy.
- Pipeline chỉ `COMPLETED` khi output đã tồn tại, checksum/metadata được QC và event complete đã persist.

## 6. Persistence đề xuất

- `workflow_instances`: job, trạng thái, pipeline version, input/config snapshot.
- `workflow_stages`: stage, status, attempt, worker task ID, timestamps, input/output reference.
- `processed_messages`: event ID, consumer, processed time để idempotent.
- `scheduled_actions`: retry/timeouts/reconciliation cần chạy.
- `outbox_events`: publish event transactionally.
- `workflow_errors`: code, retryable, sanitized detail, provider status.

Không chia sẻ quyền ghi các bảng nghiệp vụ của `tool`. `toolqueue` dùng database/schema riêng và đồng bộ kết quả bằng event.

## 7. Retry, timeout và backpressure

- Exponential backoff có jitter, ví dụ 30s -> 2m -> 10m; cấu hình theo stage.
- Lỗi 4xx/provider invalid input thường non-retryable; 429/5xx/network thường retryable có giới hạn.
- TTS/STT API cần circuit breaker và concurrency limit theo provider.
- Prefetch thấp cho render/GPU job dài; không để một consumer giữ quá nhiều message unacked.
- Job dài phải heartbeat/progress; thiếu heartbeat quá ngưỡng thì reconciliation, không vội chạy duplicate.
- DLQ phải có API/admin tool để inspect và redrive có kiểm soát.
- Retry chỉ tăng `attempt`; output path theo attempt và chỉ promote output hợp lệ để tránh file hỏng ghi đè.

## 8. Các phase triển khai

### Phase 0 - Contract và nền tảng RabbitMQ

Mục tiêu: gửi/nhận message tin cậy ở local.

- Dọn dependency: chỉ giữ Spring AMQP, JPA, validation/actuator/security thật sự cần; không dùng RabbitMQ Stream nếu chưa có use case stream.
- Tạo package `messaging`, `workflow`, `stage`, `outbox`, `recovery`, `shared`.
- Khai báo exchange/queue/binding bằng code/config có version.
- Message envelope, JSON schema và validator thống nhất với `tool`/worker.
- Correlation ID, structured logging, health/readiness.
- Docker Compose RabbitMQ có management UI; integration test bằng Testcontainers.

**Hoàn thành khi:** publish/consume được command mẫu, message lỗi vào DLQ, test chứng minh restart không mất state.

### Phase 1 - Orchestrator pipeline tuyến tính

Mục tiêu: chạy được pipeline MVP theo thứ tự.

- Consumer `pipeline.requested` idempotent.
- Persist workflow/config snapshot trước khi dispatch.
- State machine tuyến tính: probe -> extract -> transcribe -> translate -> synthesize -> subtitle -> render -> QC.
- Consumer started/progress/completed/failed.
- Outbox publisher cho mọi event outbound.
- Event progress được throttle, ví dụ tối đa một event/1-2 giây/job.

**Hoàn thành khi:** duplicate command không tạo workflow mới và một fake worker có thể đưa job từ requested tới completed.

### Phase 2 - Retry, DLQ, timeout và cancel

Mục tiêu: pipeline tự phục hồi với lỗi tạm thời.

- Error taxonomy và retry policy theo stage/provider.
- Delayed retry bằng delayed-message plugin hoặc TTL + retry queues; ghi rõ lựa chọn trong ADR.
- Heartbeat, stage deadline và reconciliation scheduler.
- Cooperative cancellation và cleanup command.
- DLQ inspection/redrive có audit.
- Circuit breaker/concurrency controls cho external provider.

**Hoàn thành khi:** test được worker crash, provider 429, timeout, poison message, cancel giữa render và redrive DLQ mà không duplicate output.

### Phase 3 - DAG, parallel stage và render lại một phần

Mục tiêu: giảm tổng thời gian và chi phí xử lý.

- Chuyển pipeline definition sang DAG có version.
- Chạy scene detection/diarization song song khi dependency cho phép.
- Cache/reuse artifact theo `input checksum + config/model version`.
- Nhánh `RETRANSLATE`, `REDUB`, `RERENDER_SUBTITLE` chỉ chạy downstream cần thiết.
- Fan-out theo chunk cho video dài, fan-in theo manifest; không truyền transcript lớn trong message.

**Hoàn thành khi:** thay subtitle style chỉ render lại video, thay voice không chạy lại STT và kết quả chunk được ghép đúng thứ tự.

### Phase 4 - Scheduling và tối ưu tài nguyên

Mục tiêu: vận hành CPU/GPU/provider quota hiệu quả.

- Queue/routing riêng cho CPU, GPU, render và priority tier.
- Fair scheduling theo workspace, quota concurrent job và duration.
- Cost estimate trước dispatch; dừng sớm khi vượt quota.
- Provider fallback có quy tắc chất lượng/ngôn ngữ rõ ràng.
- Autoscaling signal từ queue depth, oldest message age và GPU utilization.

**Hoàn thành khi:** một workspace không chiếm hết worker, priority hoạt động và autoscaling không tạo retry storm.

### Phase 5 - Observability và production hardening

Mục tiêu: xác định nhanh job chậm/hỏng và phục hồi có kiểm soát.

- OpenTelemetry trace context qua AMQP headers.
- Metrics: queue depth, age, stage latency, success rate, retry/DLQ, active jobs, cost/minute.
- Dashboard/alert/runbook cho từng terminal failure.
- Schema compatibility test, rolling deployment và graceful shutdown.
- Chaos test broker restart, network partition, duplicate/out-of-order event.

**Hoàn thành khi:** truy vết được một job xuyên service, deploy không làm mất message và có SLO theo stage/pipeline.

## 9. Security và an toàn dữ liệu

- Worker chỉ nhận object key có namespace hợp lệ, không nhận arbitrary local path hoặc URL để tránh SSRF/path traversal.
- Service account có quyền storage tối thiểu theo bucket/prefix.
- Event lỗi phải sanitize; không đưa transcript/secret vào DLQ nếu không cần.
- Command nội bộ được xác thực bằng network policy + service credential; không tin `userId` từ message nếu thiếu nguồn đã xác minh.
- Nội dung voice clone/lip-sync phải có consent/policy flag trước khi dispatch.

## 10. Definition of Done chung

- At-least-once delivery nhưng hiệu ứng nghiệp vụ effectively-once nhờ idempotency.
- Mọi transition persist trước khi ack message.
- Không có retry vô hạn, ack sớm hoặc swallow exception.
- Có test duplicate, out-of-order, crash/restart và poison message.
- Pipeline/event version được ghi trong DB/log và cập nhật contract cùng code.

