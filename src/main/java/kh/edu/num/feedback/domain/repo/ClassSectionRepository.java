package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.ClassSection;
import kh.edu.num.feedback.domain.entity.ShiftTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {

  @Query("select s from ClassSection s where s.teacher.id = :teacherUserId")
  List<ClassSection> findByTeacherId(@Param("teacherUserId") Long teacherUserId);

  @Query("select s from ClassSection s where s.semester.id = :semesterId")
  List<ClassSection> findBySemesterId(@Param("semesterId") Long semesterId);

  // For schedule linking (old logic)
  Optional<ClassSection> findFirstBySemester_IdAndCourse_IdAndTeacher_IdAndShiftTimeAndBuildingAndRoomAndSectionName(
      Long semesterId,
      Long courseId,
      Long teacherId,
      ShiftTime shiftTime,
      String building,
      String room,
      String sectionName
  );

  // Prevent duplicate group creation
  Optional<ClassSection> findFirstBySemester_IdAndCohort_IdAndGroupNoAndShiftTimeAndBuildingAndRoom(
      Long semesterId,
      Long cohortId,
      Integer groupNo,
      ShiftTime shiftTime,
      String building,
      String room
  );

  // Load all sections for dropdown filtering
  List<ClassSection> findBySemester_IdAndCohort_Id(
      Long semesterId,
      Long cohortId
  );

  // Distinct group numbers for dropdown
  @Query("""
      select distinct s.groupNo
      from ClassSection s
      where s.semester.id = :semesterId
        and s.cohort.id = :cohortId
        and s.groupNo is not null
      order by s.groupNo
  """)
  List<Integer> findDistinctGroupNos(
      @Param("semesterId") Long semesterId,
      @Param("cohortId") Long cohortId
  );

  // For default shift/building/room auto-fill
  Optional<ClassSection> findFirstBySemester_IdAndCohort_IdAndGroupNo(
      Long semesterId,
      Long cohortId,
      Integer groupNo
  );
}