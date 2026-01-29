## REST API examples (all endpoints)

- Base URL: `http://localhost:8080`
- Base path: `/schedule`
- Most endpoints use header `X-Session-Id`. Use `"default"` if unsure.

### GET /schedule

```shell
curl -X GET "http://localhost:8080/schedule" ^
  -H "X-Session-Id: default"
```

### GET /schedule/lines

```shell
curl -X GET "http://localhost:8080/schedule/lines"
```

### GET /schedule/serviceTypes

```shell
curl -X GET "http://localhost:8080/schedule/serviceTypes"
```

### POST /schedule/refreshData

```shell
curl -X POST "http://localhost:8080/schedule/refreshData"
```

### POST /schedule/work

```shell
curl -X POST "http://localhost:8080/schedule/work" ^
  -H "X-Session-Id: default"
```

### POST /schedule/init

```shell
curl -X POST "http://localhost:8080/schedule/init" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"startDate\":\"2026-01-19\"}"
```

### POST /schedule/selection

```shell
curl -X POST "http://localhost:8080/schedule/selection" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"selection\":{\"12345\":true,\"67890\":false}}"
```

### POST /schedule/lineStart

```shell
curl -X POST "http://localhost:8080/schedule/lineStart" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"startLineDateTime\":\"2026-01-19T08:00\"}"
```

### POST /schedule/lineMaxEnd

```shell
curl -X POST "http://localhost:8080/schedule/lineMaxEnd" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"lineMaxEndDateTime\":\"2026-01-19T18:00\"}"
```

### POST /schedule/updateOrderList

```shell
curl -X POST "http://localhost:8080/schedule/updateOrderList" ^
  -H "X-Session-Id: default"
```

### POST /schedule/sortByNp

```shell
curl -X POST "http://localhost:8080/schedule/sortByNp" ^
  -H "X-Session-Id: default"
```

### POST /schedule/solve

```shell
curl -X POST "http://localhost:8080/schedule/solve" ^
  -H "X-Session-Id: default"
```

### POST /schedule/stopSolving

```shell
curl -X POST "http://localhost:8080/schedule/stopSolving" ^
  -H "X-Session-Id: default"
```

### POST /schedule/moveJobs

```shell
curl -X POST "http://localhost:8080/schedule/moveJobs" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"fromLineId\":\"170610020000\",\"toLineId\":\"170610020001\",\"fromIndex\":0,\"count\":2,\"insertIndex\":1}"
```

### POST /schedule/maintenance

Add/update/remove maintenance:

```shell
curl -X POST "http://localhost:8080/schedule/maintenance" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"insertIndex\":0,\"durationMinutes\":30,\"maintenanceTypeId\":2,\"maintenanceNote\":\"Note\"}"
```

Update maintenance type (updateIndex):

```shell
curl -X POST "http://localhost:8080/schedule/maintenance" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"updateIndex\":0,\"maintenanceTypeId\":3}"
```

Remove maintenance (removeIndex):

```shell
curl -X POST "http://localhost:8080/schedule/maintenance" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"removeIndex\":0}"
```

### POST /schedule/pin

```shell
curl -X POST "http://localhost:8080/schedule/pin" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"pinCount\":3,\"pinAll\":false}"
```

### POST /schedule/save

```shell
curl -X POST "http://localhost:8080/schedule/save" ^
  -H "X-Session-Id: default"
```

### PUT /schedule/analyze

Without fetchPolicy:

```shell
curl -X PUT "http://localhost:8080/schedule/analyze" ^
  -H "X-Session-Id: default"
```

With fetchPolicy:

```shell
curl -X PUT "http://localhost:8080/schedule/analyze?fetchPolicy=FULL" ^
  -H "X-Session-Id: default"
```

