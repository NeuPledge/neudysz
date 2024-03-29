package com.github.tangyi.exam.service.course;

import com.github.tangyi.api.exam.dto.CourseSectionDto;
import com.github.tangyi.api.exam.model.ExamCourseSection;
import com.github.tangyi.api.exam.service.IExamCourseKnowledgePointService;
import com.github.tangyi.api.exam.service.IExamCourseSectionService;
import com.github.tangyi.common.service.CrudService;
import com.github.tangyi.constants.ExamCacheName;
import com.github.tangyi.exam.mapper.ExamCourseSectionMapper;
import com.github.tangyi.exam.service.media.ExamMediaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程节
 */
@Slf4j
@Service
@AllArgsConstructor
public class ExamCourseSectionService extends CrudService<ExamCourseSectionMapper, ExamCourseSection>
		implements IExamCourseSectionService, ExamCacheName {

	private final ExamMediaService mediaService;
	private final IExamCourseKnowledgePointService knowledgePointService;

	@Override
	@Cacheable(value = ExamCacheName.SECTION, key = "#id")
	public ExamCourseSection get(Long id) {
		return super.get(id);
	}

	public CourseSectionDto watchSection(Long id) {
		CourseSectionDto dto = new CourseSectionDto();
		ExamCourseSection section = this.get(id);
		if (section != null && section.getVideoId() != null && section.getVideoUrl() == null) {
			section.setVideoUrl(mediaService.videoUrl(section.getVideoId()));
		}
		if (section != null && section.getSpeechId() != null && section.getSpeechUrl() == null) {
			section.setSpeechUrl(mediaService.videoUrl(Long.valueOf(section.getSpeechId())));
		}
		dto.setSection(section);
		return dto;
	}

	@Override
	@Transactional
	public int insert(ExamCourseSection examCourseSection) {
		examCourseSection.setCommonValue();
		if (examCourseSection.getSpeechId() != null && examCourseSection.getSpeechUrl() == null){
			examCourseSection.setSpeechUrl(mediaService.videoUrl(Long.valueOf(examCourseSection.getSpeechId())));
		}
		if (examCourseSection.getVideoId() != null && examCourseSection.getVideoUrl() == null){
			examCourseSection.setVideoUrl(mediaService.videoUrl(examCourseSection.getVideoId()));
		}
		return super.insert(examCourseSection);
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.SECTION, key = "#examCourseSection.id")
	public int update(ExamCourseSection examCourseSection) {
		examCourseSection.setCommonValue();
		if (examCourseSection.getSpeechId() != null && examCourseSection.getSpeechUrl() == null){
			examCourseSection.setSpeechUrl(mediaService.videoUrl(Long.valueOf(examCourseSection.getSpeechId())));
		}
		if (examCourseSection.getVideoId() != null && examCourseSection.getVideoUrl() == null){
			examCourseSection.setVideoUrl(mediaService.videoUrl(examCourseSection.getVideoId()));
		}
		return super.update(examCourseSection);
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.SECTION, key = "#examCourseSection.id")
	public int delete(ExamCourseSection examCourseSection) {
		return super.delete(examCourseSection);
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.SECTION, allEntries = true)
	public int deleteAll(Long[] ids) {
		return super.deleteAll(ids);
	}

	public List<ExamCourseSection> findSectionsByChapterId(Long id) {
		return this.dao.findSectionsByChapterId(id);
	}
}
