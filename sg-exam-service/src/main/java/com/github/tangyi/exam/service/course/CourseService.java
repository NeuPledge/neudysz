package com.github.tangyi.exam.service.course;

import com.beust.jcommander.internal.Maps;
import com.github.pagehelper.PageInfo;
import com.github.tangyi.api.exam.dto.CourseChapterDto;
import com.github.tangyi.api.exam.dto.CourseDetailDto;
import com.github.tangyi.api.exam.dto.CourseSectionDto;
import com.github.tangyi.api.exam.model.*;
import com.github.tangyi.api.exam.service.ICourseService;
import com.github.tangyi.api.user.attach.AttachmentManager;
import com.github.tangyi.api.user.model.Attachment;
import com.github.tangyi.api.user.model.User;
import com.github.tangyi.api.user.service.IAttachmentService;
import com.github.tangyi.api.user.service.IUserService;
import com.github.tangyi.common.constant.Group;
import com.github.tangyi.common.constant.Status;
import com.github.tangyi.common.exceptions.CommonException;
import com.github.tangyi.common.lucene.DocType;
import com.github.tangyi.common.lucene.IndexCrudParam;
import com.github.tangyi.common.service.CrudService;
import com.github.tangyi.common.utils.SysUtil;
import com.github.tangyi.constants.ExamCacheName;
import com.github.tangyi.constants.ExamConstant;
import com.github.tangyi.exam.mapper.CourseMapper;
import com.github.tangyi.exam.service.ExamPermissionService;
import com.github.tangyi.exam.service.fav.CourseFavoritesService;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@AllArgsConstructor
public class CourseService extends CrudService<CourseMapper, Course> implements ICourseService, ExamConstant {

	private final IUserService userService;
	private final IAttachmentService attachmentService;
	private final AttachmentManager attachmentManager;
	private final ExamCourseChapterService chapterService;
	private final ExamCourseSectionService sectionService;
	private final ExamCourseKnowledgePointService knowledgePointService;
	private final ExamCourseMemberService memberService;
	private final CourseFavoritesService courseFavoritesService;
	private final CourseIdFetcher courseIdFetcher;
	private final ExamPermissionService examPermissionService;

	@Override
	public Long findAllCourseCount() {
		return this.dao.findAllCourseCount();
	}

	@Override
	@Cacheable(value = ExamCacheName.COURSE, key = "#id")
	public Course get(Long id) {
		Course course = super.get(id);
		if (course != null) {
			this.initCourseInfo(Collections.singletonList(course));
		}
		return course;
	}

	public List<Long> findAllIds() {
		return this.courseIdFetcher.fetchAll(0, Collections.emptyMap());
	}

	@Override
	public PageInfo<Course> userCourseList(Map<String, Object> params, int pageNum, int pageSize) {
		Long userId = SysUtil.getUserId();
		params.put("userId", userId);
		User user = userService.get(userId);
		Preconditions.checkNotNull(user, "user not found");
		Long deptId = user.getDeptId();
		if (deptId != null) {
			params.put("deptId", deptId);
		}
		return this.findUserCourses(params, pageNum, pageSize);
	}

	@Override
	public PageInfo<Course> findUserCourses(Map<String, Object> params, int pageNum, int pageSize) {
		this.commonPageParam(params, pageNum, pageSize);
		List<Course> courses = this.dao.findUserCourses(params);
		if (CollectionUtils.isNotEmpty(courses)) {
			this.initCourseInfo(courses);
		}
		return new PageInfo<>(courses);
	}

	@Override
	public List<Course> popularCourses(String findFav) {
		Map<String, Object> params = Maps.newHashMap();
		// 只查询无权限控制的课程
		params.put("accessType", ExamConstant.ACCESS_TYPE_ALL);
		PageInfo<Course> page = this.findPage(params, 1, 10);
		List<Course> courses = page.getList();
		if (Boolean.parseBoolean(findFav)) {
			courseFavoritesService.findAndFillUserFavorites(courses);
			courseFavoritesService.findAndFillFavCount(courses);
		}
		return courses;
	}

	@Override
	public PageInfo<Course> findPage(Map<String, Object> params, int pageNum, int pageSize) {
		PageInfo<Course> pageInfo = super.findPage(params, pageNum, pageSize);
		if (CollectionUtils.isNotEmpty(pageInfo.getList())) {
			this.initCourseInfo(pageInfo.getList());
		}
		return pageInfo;
	}

	public PageInfo<?> findUserFavoritesPage(PageInfo<ExamUserFav> page) {
		PageInfo<Course> pageInfo = new PageInfo<>();
		BeanUtils.copyProperties(page, pageInfo);
		List<Long> ids = page.getList().stream().map(ExamUserFav::getTargetId).toList();
		List<Course> courses = findListById(ids.toArray(Long[]::new));
		this.initCourseInfo(courses);
		for (Course course : courses) {
			course.setFavorite(true);
		}
		courseFavoritesService.findAndFillFavCount(courses);
		pageInfo.setList(courses);
		return pageInfo;
	}

	@Override
	@Transactional
	public int insert(Course course) {
		// 没有上传图片，使用默认图片
		if (course.getImageId() == null) {
			course.setImageId(attachmentManager.defaultImage(Group.DEFAULT));
		}
		int update = super.insert(course);
		if (update > 0) {
			this.addCourseMembers(course, SysUtil.getUser(), SysUtil.getTenantCode());
			this.addIndex(course, 0, 0);
		}
		return update;
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.COURSE, key = "#course.id")
	public int update(Course course) {
		int update = super.update(course);
		if (update > 0) {
			this.examPermissionService.deletePermission(PERMISSION_TYPE_COURSE, course.getId());
			this.addCourseMembers(course, SysUtil.getUser(), SysUtil.getTenantCode());
			this.updateIndex(course);
		}
		return update;
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.COURSE, key = "#course.id")
	public int delete(Course course) {
		Attachment attachment = new Attachment();
		attachment.setId(course.getImageId());
		attachmentService.delete(attachment);
		int update = super.delete(course);
		if (update > 0) {
			super.deleteIndex(course.getId(), DocType.COURSE);
		}
		return update;
	}

	@Override
	@Transactional
	@CacheEvict(value = ExamCacheName.COURSE, allEntries = true)
	public int deleteAll(Long[] ids) {
		int update = 0;
		for (Long id : ids) {
			Course course = this.get(id);
			update += this.delete(course);
		}
		return update;
	}

	@Override
	public void addIndex(Course course, long clickCnt, long joinCnt) {
		super.addIndex(this.buildIndexCrudParam(course, joinCnt, joinCnt));
	}

	@Override
	public void updateIndex(Course course) {
		ExamCourseMember member = new ExamCourseMember();
		member.setCourseId(course.getId());
		Integer memberCnt = memberService.findMemberCountByCourseId(member);
		long joinCnt = memberCnt == null ? 0 : memberCnt;
		super.updateIndex(this.buildIndexCrudParam(course, joinCnt, joinCnt));
	}

	public void initCourseInfo(List<Course> courses) {
		ExamCourseMember member = new ExamCourseMember();
		for (Course course : courses) {
			// 图片
			String imageUrl = null;
			if (course.getImageId() != null && course.getImageId() != 0L) {
				imageUrl = attachmentManager.getPreviewUrlIgnoreException(course.getImageId());
			}
			course.setImageUrl(imageUrl);
			// 报名人数
			member.setCourseId(course.getId());
			course.setMemberCount(memberService.findMemberCountByCourseId(member));
		}
	}

	/**
	 * 查询课程的详细信息
	 */
	public CourseDetailDto getDetail(Long id) {
		CourseDetailDto dto = new CourseDetailDto();
		// 课时
		AtomicLong learnHour = new AtomicLong(0);
		// 课程信息
		Course course = this.get(id);
		// 章节
		List<CourseChapterDto> chapters = findChapters(id, learnHour);
		dto.setCourse(course);
		dto.setChapters(chapters);
		dto.setChapterSize(CollectionUtils.size(chapters) + "");
		dto.setLearnHour(learnHour.get() + " 小时");
		// 学员数量
		ExamCourseMember member = new ExamCourseMember();
		member.setCourseId(id);
		member.setUserId(SysUtil.getUserId());
		dto.setMemberCount(memberService.findMemberCountByCourseId(member) + "");
		// 是否已报名
		dto.setIsUserJoin(memberService.findByCourseIdAndUserId(id, SysUtil.getUserId()) != null);
		return dto;
	}

	public List<CourseChapterDto> findChapters(Long id, AtomicLong learnHour) {
		List<CourseChapterDto> chapterDtos = Lists.newArrayList();
		List<ExamCourseChapter> chapters = chapterService.findChaptersByCourseId(id);
		if (CollectionUtils.isNotEmpty(chapters)) {
			for (ExamCourseChapter chapter : chapters) {
				CourseChapterDto chapterDto = new CourseChapterDto();
				List<CourseSectionDto> sections = findSections(chapter, learnHour);
				chapterDto.setChapter(chapter);
				chapterDto.setSections(sections);
				chapterDtos.add(chapterDto);
			}
		}
		return chapterDtos;
	}

	private List<CourseSectionDto> findSections(ExamCourseChapter chapter, AtomicLong learnHour) {
		List<CourseSectionDto> dtoList = Lists.newArrayList();
		List<ExamCourseSection> sections = sectionService.findSectionsByChapterId(chapter.getId());
		if (CollectionUtils.isNotEmpty(sections)) {
			for (ExamCourseSection section : sections) {
				if (section.getLearnHour() != null) {
					learnHour.addAndGet(section.getLearnHour());
				}
				CourseSectionDto dto = new CourseSectionDto();
				dto.setSection(section);
				dto.setPoints(knowledgePointService.getPoints(section.getId()));
				dtoList.add(dto);
			}
		}
		return dtoList;
	}

	@Transactional
	public Boolean joinCourse(Long courseId, Long userId, String type) {
		ExamCourseMember member = new ExamCourseMember();
		member.setCourseId(courseId);
		member.setUserId(userId);
		// 报名
		if (Status.OPEN.equals(type)) {
			ExamCourseMember exist = memberService.findByCourseIdAndUserId(courseId, userId);
			if (exist != null) {
				throw new CommonException("不能重复加入");
			}

			return memberService.insert(member) > 0;
		} else {
			// 取消报名
			return memberService.deleteByCourseIdAndUserId(member) > 0;
		}
	}

	@Override
	public CourseDetailDto getCourseAttach(Long courseId) {
		Course course = this.get(courseId);
		if (course != null && course.getAttachId() != null) {
			Attachment attachment = attachmentService.get(course.getAttachId());
			if (attachment != null) {
				CourseDetailDto dto = new CourseDetailDto();
				dto.setAttachName(attachment.getAttachName());
				dto.setAttachUrl(attachment.getUrl());
				return dto;
			}
		}
		return null;
	}

	@Transactional
	public void addCourseMembers(Course course, String user, String tenantCode) {
		// 未发布
		if (!EXAM_STATUS_PUBLISHED.equals(course.getCourseStatus())) {
			return;
		}
		Long id = course.getId();
		// 用户 ID
		examPermissionService.addPermissions(id, course.getMembers(), PERMISSION_TYPE_COURSE, PERMISSION_ID_TYPE_USER,
				user, tenantCode);
		// 部门 ID
		String deptMember = course.getDeptMember();
		if (StringUtils.isNotEmpty(deptMember)) {
			examPermissionService.addPermissions(id, Collections.singletonList(Long.valueOf(deptMember)),
					PERMISSION_TYPE_COURSE, PERMISSION_ID_TYPE_DEPT, user, tenantCode);
		}
	}

	private IndexCrudParam buildIndexCrudParam(Course course, long clickCnt, long joinCnt) {
		return IndexCrudParam.builder() //
				.id(course.getId()) //
				.type(DocType.COURSE) //
				.contents(Lists.newArrayList(course.getCourseName(), course.getCourseDescription())) //
				.updateTime(course.getUpdateTime().getTime()) //
				.clickCnt(clickCnt) //
				.joinCnt(joinCnt) //
				.build(); //
	}
}
