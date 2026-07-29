---
name: "파일 스토리지 (S3 Compatible)"
description: "파일 업로드·다운로드·접근 제어의 범용 표준. 오브젝트 스토리지·Presigned URL·멀웨어 스캔·퍼블릭 쓰기 차단·UUID 재명명을 다룬다. 파일 업로드/다운로드 기능을 설계하거나 스토리지 접근 권한·보안을 정할 때 읽는다. 언어/프레임워크 무관 범용 표준. 키워드: s3, storage, presigned, upload, file, bucket, minio, gcs, object storage, malware scan."
---

# 파일 스토리지 (S3 Compatible)

**ID:** `SKL-FILE-STORAGE`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 파일 업로드·다운로드·접근 제어의 범용 표준. 오브젝트 스토리지·Presigned URL·멀웨어 스캔·퍼블릭 쓰기 차단·UUID 재명명을 다룬다. 파일 업로드/다운로드 기능을 설계하거나 스토리지 접근 권한·보안을 정할 때 읽는다. 언어/프레임워크 무관 범용 표준. 키워드: s3, storage, presigned, upload, file, bucket, minio, gcs, object storage, malware scan.

---

## 지시사항 (Instructions)

1. 파일을 애플리케이션 서버의 로컬 디스크에 저장하지 않는다: 오브젝트 스토리지(S3·GCS·MinIO)를 사용한다.
2. 대용량 파일은 Presigned URL로 클라이언트가 스토리지에 직접 업로드해 애플리케이션 서버를 우회한다.
3. 업로드된 파일은 바이러스/멀웨어 스캔을 통과한 뒤에만 사용 가능 상태로 전환한다.
4. 버킷은 퍼블릭 쓰기를 차단하고, 읽기도 필요한 경우에만 허용한다: 민감 파일은 Presigned URL로만 제공한다.
5. 파일명은 사용자 입력을 그대로 쓰지 않고 UUID/해시로 재명명해 경로 탐색 공격을 막는다.

## 태그

`s3` `storage` `presigned` `upload` `file` `bucket` `minio` `gcs` `object storage` `malware scan` `object-storage` `download` `access-control` `malware-scan` `file-storage` `backEnd` `ai-recommended`
