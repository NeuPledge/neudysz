package com.github.tangyi.api.exam.service;

import com.github.tangyi.api.exam.dto.CourseDetailDto;
import com.github.tangyi.api.exam.model.Course;
import com.github.tangyi.common.service.ICrudService;

import java.util.List;

public interface ICourseService extends ICrudService<Course> {

	Long findAllCourseCount();

	List<Long> findAllIds();

	List<Course> popularCourses(String findFav);

	CourseDetailDto getDetail(Long id);

	Boolean joinCourse(Long courseId, Long userId, String type);

	CourseDetailDto getCourseAttach(Long courseId);
}
