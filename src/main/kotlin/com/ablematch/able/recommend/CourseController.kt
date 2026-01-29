package com.ablematch.able.recommend

import com.ablematch.able.dto.RecommendedCourseDto
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/courses")
class CourseController(
    private val courseService: CourseRecommendationService
) {

    @GetMapping("/by-skill")
    fun recommendBySkill(
        @RequestParam skill: String
    ): List<RecommendedCourseDto> {
        return listOf(courseService.recommendBySkill(skill))
    }
    @GetMapping("/by-skills")
    fun recommendBySkills(
        @RequestParam skills: List<String>
    ): List<RecommendedCourseDto> {
        return courseService.recommendBySkills(skills)
    }
}
