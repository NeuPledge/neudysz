package com.github.tangyi.exam.controller.course;

import com.github.pagehelper.PageInfo;
import com.github.tangyi.api.exam.dto.CourseDetailDto;
import com.github.tangyi.api.exam.dto.MemberDto;
import com.github.tangyi.api.exam.model.Course;
import com.github.tangyi.api.exam.service.ICourseService;
import com.github.tangyi.api.exam.service.IExamPermissionService;
import com.github.tangyi.common.base.BaseController;
import com.github.tangyi.common.constant.Status;
import com.github.tangyi.common.log.OperationType;
import com.github.tangyi.common.log.SgLog;
import com.github.tangyi.common.model.R;
import com.github.tangyi.common.utils.SysUtil;
import com.github.tangyi.constants.ExamConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Tag(name = "课程信息管理")
@RestController
@RequestMapping("/v1/course")
public class CourseController extends BaseController {

	private final ICourseService courseService;
	private final IExamPermissionService examPermissionService;

	@GetMapping("/{id}")
	@Operation(summary = "获取课程信息")
	public R<Course> get(@PathVariable Long id) {
		return R.success(courseService.get(id));
	}

	@GetMapping("courseList")
	@Operation(summary = "获取课程列表")
	public R<PageInfo<Course>> list(@RequestParam Map<String, Object> condition,
			@RequestParam(value = PAGE, required = false, defaultValue = PAGE_DEFAULT) int pageNum,
			@RequestParam(value = PAGE_SIZE, required = false, defaultValue = PAGE_SIZE_DEFAULT) int pageSize) {
		return R.success(courseService.findPage(condition, pageNum, pageSize));
	}

	@GetMapping("userCourseList")
	@Operation(summary = "获取用户有权限的课程列表")
	public R<PageInfo<Course>> userCourseList(@RequestParam Map<String, Object> condition,
			@RequestParam(value = PAGE, required = false, defaultValue = PAGE_DEFAULT) int pageNum,
			@RequestParam(value = PAGE_SIZE, required = false, defaultValue = PAGE_SIZE_DEFAULT) int pageSize) {
		return R.success(courseService.userCourseList(condition, pageNum, pageSize));
	}

	@GetMapping("allCourses")
	@Operation(summary = "获取全部课程列表")
	public R<List<Course>> allCourses(Course course) {
		course.setTenantCode(SysUtil.getTenantCode());
		return R.success(courseService.findAllList(course));
	}

	@GetMapping("popularCourses")
	@Operation(summary = "获取热门课程列表")
	public R<List<Course>> popularCourses(@RequestParam(required = false, defaultValue = "true") String findFav) {
		return R.success(courseService.popularCourses(findFav));
	}

	@GetMapping("/{id}/getMembers")
	@Operation(summary = "获取课程成员 ID", description = "根据课程 ID 获取课程成员 ID")
	public R<MemberDto> getMembers(@PathVariable Long id) {
		return R.success(examPermissionService.getMembers(ExamConstant.PERMISSION_TYPE_COURSE, id));
	}

	@PostMapping
	@Operation(summary = "创建课程")
	@SgLog(value = "新增课程", operationType = OperationType.INSERT)
	public R<Boolean> add(@RequestBody @Valid Course course) {
		course.setCommonValue();
		return R.success(courseService.insert(course) > 0);
	}

	@PutMapping("{id}")
	@Operation(summary = "更新课程信息")
	@SgLog(value = "更新课程", operationType = OperationType.UPDATE)
	public R<Boolean> update(@PathVariable Long id, @RequestBody @Valid Course course) {
		course.setId(id);
		course.setCommonValue();
		return R.success(courseService.update(course) > 0);
	}

	@DeleteMapping("{id}")
	@Operation(summary = "删除课程")
	@SgLog(value = "删除课程", operationType = OperationType.DELETE)
	public R<Boolean> delete(@PathVariable Long id) {
		Course course = courseService.get(id);
		course.setCommonValue();
		return R.success(courseService.delete(course) > 0);
	}

	@PostMapping("deleteAll")
	@Operation(summary = "批量删除课程")
	@SgLog(value = "批量删除课程", operationType = OperationType.DELETE)
	public R<Boolean> deleteAllCourses(@RequestBody Long[] ids) {
		return R.success(courseService.deleteAll(ids) > 0);
	}

	@GetMapping("/detail/{id}")
	@Operation(summary = "获取课程详细信息")
	public R<CourseDetailDto> getDetail(@PathVariable Long id) {
		return R.success(courseService.getDetail(id));
	}

	@PostMapping("{id}/join")
	@Operation(summary = "加入课程")
	@SgLog(value = "加入课程", operationType = OperationType.INSERT)
	public R<Boolean> joinCourse(@PathVariable Long id,
			@RequestParam(required = false, defaultValue = Status.OPEN) String type) {
		return R.success(courseService.joinCourse(id, SysUtil.getUserId(), type));
	}

	@GetMapping("{id}/attach")
	@Operation(summary = "获取课程课件")
	public R<CourseDetailDto> getCourseAttach(@PathVariable Long id) {
		return R.success(courseService.getCourseAttach(id));
	}
}
