package com.github.tangyi.exam.mapper;

import com.github.tangyi.api.exam.model.ExamCourseSection;
import com.github.tangyi.common.base.CrudMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 课程节Mapper
 *
 * @author tangyi
 * @date 2022-11-21
 */
@Repository
public interface ExamCourseSectionMapper extends CrudMapper<ExamCourseSection> {

	List<ExamCourseSection> findSectionsByChapterId(Long id);
}