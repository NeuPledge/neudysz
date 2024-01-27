package com.github.tangyi.user.service.attach;

import cn.hutool.core.io.resource.ResourceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.tangyi.api.user.attach.BytesUploadContext;
import com.github.tangyi.api.user.attach.MultipartFileUploadContext;
import com.github.tangyi.api.user.model.AttachGroup;
import com.github.tangyi.api.user.model.Attachment;
import com.github.tangyi.api.user.model.SysAttachmentChunk;
import com.github.tangyi.common.exceptions.CommonException;
import com.github.tangyi.common.oss.config.MinioConfig;
import com.github.tangyi.common.oss.exceptions.OssException;
import com.github.tangyi.common.utils.StopWatchUtil;
import com.github.tangyi.user.thread.ExecutorHolder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.micrometer.core.instrument.util.IOUtils;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioAttachmentStorage extends AbstractAttachmentStorage {

	private final MinioConfig minioConfig;
	private final Map<String, String> contentTypeMap;
	private final SysAttachmentChunkService attachmentChunkService;

	private MinioClient minioClient;

	public MinioAttachmentStorage(AttachmentService attachmentService, AttachGroupService groupService,
			MinioConfig minioConfig, DefaultImageService defaultImageService, ExecutorHolder executorHolder,
			SysAttachmentChunkService attachmentChunkService) {
		super(attachmentService, groupService, defaultImageService, executorHolder);
		this.minioConfig = minioConfig;
		this.contentTypeMap = Maps.newHashMap();
		this.attachmentChunkService = attachmentChunkService;
		this.initContentTypes();
		this.initMinioClient();
	}

	private void initContentTypes() {
		try {
			long startNs = System.nanoTime();
			String str = IOUtils.toString(ResourceUtil.getStream("content_type.json"), StandardCharsets.UTF_8);
			JSONObject jsonObject = JSON.parseObject(str);
			if (jsonObject != null) {
				for (String key : jsonObject.keySet()) {
					contentTypeMap.put(key, jsonObject.getString(key));
				}
			}
			long took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
			log.info("Init content type finished, size: {}, took: {}ms", contentTypeMap.size(), took);
		} catch (Exception e) {
			log.error("Failed to init content types.", e);
		}
	}

	private void initMinioClient() {
		if (!minioConfig.isEnabled()) {
			log.warn("MinIO is disabled.");
			return;
		}

		if (StringUtils.isEmpty(minioConfig.getAccessKey())) {
			log.error("MinIO accessKey is empty.");
			return;
		}

		if (StringUtils.isEmpty(minioConfig.getSecretKey())) {
			log.error("MinIO secretKey is empty.");
			return;
		}

		try {
			this.minioClient = MinioClient.builder().endpoint(minioConfig.getEndpoint())
					.credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey()).build();
			if (StringUtils.isNotEmpty(minioConfig.getBucket())) {
				checkOrCreateBucket(minioConfig.getBucket());
			}
		} catch (Exception e) {
			log.error("Failed to init MinIO client.", e);
		}
	}

	private void checkOrCreateBucket(String bucket) {
		try {
			if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				log.info("Start to make bucket: {}", bucket);
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
				log.info("Make bucket finish, bucket: {}", bucket);
			}
		} catch (ConnectException e) {
			log.warn("Failed to connect to MinIO.");
		} catch (Exception e) {
			log.error("Failed to check or create bucket: {}", bucket, e);
		}
	}

	private String getContentType(String fileName) {
		String ext = "." + FilenameUtils.getExtension(fileName);
		String contentType = this.contentTypeMap.get(ext);
		if (StringUtils.isEmpty(contentType)) {
			contentType = "application/octet-stream";
		}
		return contentType;
	}

	private void deleteTempChunkFiles(List<String> tempFiles, String hash, String tenantCode) {
		CompletableFuture.runAsync(() -> {
			log.info("Start to delete temp chunk files: {}", tempFiles);
			for (String fileName : tempFiles) {
				this.doDelete(fileName);
			}
			int chunkDeleteRes = attachmentChunkService.deleteByHash(hash, tenantCode);
			log.info("Delete temp chunk files finished, files: {}, chunkDeleteRes: {}", tempFiles, chunkDeleteRes);
		});
	}

	@Override
	@Transactional
	public Attachment upload(MultipartFileUploadContext context) throws IOException {
		String groupCode = context.getGroup().getGroupCode();
		MultipartFile file = context.getMultipartFile();
		return this.upload(groupCode, file.getOriginalFilename(), file.getOriginalFilename(), file.getBytes(),
				context.getUser(), context.getTenantCode(), context.getHash());
	}

	@Override
	@Transactional
	public String uploadChunk(com.github.tangyi.api.user.attach.ChunkUploadContext context) throws IOException {
		String groupCode = context.getGroup().getGroupCode();
		MultipartFile file = context.getMultipartFile();
		Attachment attachment = prepare(groupCode, file.getOriginalFilename(), file.getOriginalFilename(),
				file.getBytes(), context.getUser(), context.getTenantCode(), context.getHash());
		String fileName = preUpload(attachment) + "_" + context.getIndex();
		String contentType = getContentType(fileName);
		try (InputStream in = new ByteArrayInputStream(file.getBytes())) {
			PutObjectArgs args = PutObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName)
					.stream(in, in.available(), -1).contentType(contentType).build();
			return minioClient.putObject(args).object();
		} catch (Exception ex) {
			throw new OssException(ex, "Failed to upload file");
		}
	}

	@Override
	public Attachment mergeChunks(Attachment prepare, AttachGroup group, List<SysAttachmentChunk> chunks)
			throws CommonException {
		List<ComposeSource> chunkSources = Lists.newArrayListWithExpectedSize(chunks.size());
		List<String> chunkNames = Lists.newArrayListWithExpectedSize(chunks.size());
		chunks.stream().sorted(Comparator.comparingInt(SysAttachmentChunk::getChunkNumber)).forEach(chunk -> {
			chunkSources.add(
					ComposeSource.builder().bucket(minioConfig.getBucket()).object(chunk.getChunkName()).build());
			chunkNames.add(chunk.getChunkName());
		});

		log.info("Start to merge chunks, size: {}", chunkSources.size());
		try {
			String key = preUpload(prepare);
			String contentType = getContentType(prepare.getAttachName());
			ObjectWriteResponse res = minioClient.composeObject(
					ComposeObjectArgs.builder().bucket(minioConfig.getBucket()).object(key).sources(chunkSources)
							.headers(Collections.singletonMap("Content-Type", contentType)).build());
			log.info("Merge chunks finished, key: {}", key);
			if (res != null && CollectionUtils.isNotEmpty(chunkNames)) {
				prepare.setUrl(this.getDownloadUrl(key, -1));
				this.deleteTempChunkFiles(chunkNames, prepare.getHash(), prepare.getTenantCode());
			}
		} catch (Exception e) {
			throw new CommonException(e, "Failed to merge chunks.");
		}
		return prepare;
	}

	@Override
	@Transactional
	public Attachment upload(BytesUploadContext context) {
		String groupCode = context.getGroup().getGroupCode();
		return this.upload(groupCode, context.getFileName(), context.getOriginalFilename(), context.getBytes(),
				context.getUser(), context.getTenantCode(), context.getHash());
	}

	@Transactional
	public Attachment upload(String groupCode, String fileName, String originalFilename, byte[] bytes, String user,
			String tenantCode, String hash) {
		Attachment attachment = prepare(groupCode, fileName, originalFilename, bytes, user, tenantCode, hash);
		upload(attachment, bytes);
		return attachment;
	}

	@Transactional
	public void upload(Attachment attachment, byte[] bytes) {
		String fileName = preUpload(attachment);
		String contentType = getContentType(fileName);
		StopWatch watch = StopWatchUtil.start();
		try (InputStream in = new ByteArrayInputStream(bytes)) {
			PutObjectArgs args = PutObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName)
					.stream(in, in.available(), -1).contentType(contentType).build();
			ObjectWriteResponse response = minioClient.putObject(args);
			doAfterUpload(attachment, response.object(), fileName, watch);
		} catch (Exception ex) {
			throw new OssException(ex, "Failed to upload file");
		}
	}

	@Transactional
	public void doAfterUpload(Attachment attachment, String key, String fileName, StopWatch watch) {
		String took = StopWatchUtil.stop(watch);
		log.info("Upload file done successfully, fileName: {}, took: {}", fileName, took);
		String result = minioConfig.getBucket() + "/" + key;
		attachment.setUploadResult(result);
		attachment.setUrl(getDownloadUrl(fileName, -1));
		if (attachment.getId() == null) {
			attachmentService.insert(attachment);
		} else {
			attachmentService.update(attachment);
		}
	}

	@Override
	public void doDelete(String fileName) {
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName).build());
			log.info("Attachment has been deleted, fileName: {}", fileName);
		} catch (Exception e) {
			if (StringUtils.contains(e.getMessage(), "no such file or directory")) {
				log.warn("Delete attachment failed: no such file or directory, fileName: {}", fileName);
			} else {
				throw new CommonException(e);
			}
		}
	}

	@Override
	public String getDownloadUrl(String fileName, long expire) {
		String url = null;
		try {
			if (expire > 0 && expire < (7 * 24 * 3600)) {
				url = minioClient.getPresignedObjectUrl(
						GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(minioConfig.getBucket())
								.object(fileName).expiry((int) expire, TimeUnit.SECONDS).build());
			} else {
				// 直接拼接出访问路径
				url = minioConfig.getEndpoint() + "/" + minioConfig.getBucket() + "/" + fileName;
			}

			// 替换访问域名
			if (StringUtils.isNotEmpty(url) && StringUtils.isNotEmpty(minioConfig.getAccessDomain())) {
				UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url).build();
				String path = uriComponents.getPath();
				String query = uriComponents.getQuery();
				url = minioConfig.getAccessDomain() + path;
				if (StringUtils.isNotEmpty(query)) {
					url = url + "?" + query;
				}
			}
		} catch (Exception e) {
			log.error("Failed to get download url, fileName: {}", fileName, e);
		}
		return url;
	}
}
