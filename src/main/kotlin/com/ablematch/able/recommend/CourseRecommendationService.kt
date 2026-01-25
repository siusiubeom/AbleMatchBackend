package com.ablematch.able.recommend

import com.ablematch.able.dto.RecommendedCourseDto
import org.springframework.stereotype.Service

@Service
class CourseRecommendationService {

    fun recommendBySkill(skill: String): RecommendedCourseDto {
        return RecommendedCourseDto(
            skill = skill,
            title = "Master $skill",
            url = "https://www.udemy.com/courses/search/?q=$skill"
        )
    }

    fun recommendBySkills(skills: List<String>): List<RecommendedCourseDto> {
        return skills.map { recommendBySkill(it) }
    }
}
