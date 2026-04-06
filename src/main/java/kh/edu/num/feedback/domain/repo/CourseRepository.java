package kh.edu.num.feedback.domain.repo;


import kh.edu.num.feedback.domain.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
  java.util.List<Course> findAllByOrderByStudyYearAscSemesterNoAscCodeAsc();
}
