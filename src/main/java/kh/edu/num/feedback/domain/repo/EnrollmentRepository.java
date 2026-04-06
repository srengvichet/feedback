package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.Enrollment;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.Submission;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.web.admin.dto.StudentFeedbackStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentId(Long studentUserId);

    boolean existsByStudentIdAndSectionId(Long studentUserId, Long sectionId);

    boolean existsByStudent_IdAndSection_Id(Long studentId, Long sectionId);

    long countBySection_Id(Long sectionId);

    interface SectionEnrollCount {
        Long getSectionId();

        Long getEnrolled();
    }

    @Query("""
              select e.section.id as sectionId, count(e.id) as enrolled
              from Enrollment e
              where e.section.semester.id = :semesterId
              group by e.section.id
            """)
    List<SectionEnrollCount> countEnrollmentsBySemesterGroupBySection(@Param("semesterId") Long semesterId);

    @Query("""
              select e
              from Enrollment e
              join fetch e.section s
              join fetch s.semester
              join fetch s.course
              join fetch s.teacher
              left join fetch s.cohort
              where e.student.id = :studentId
              order by s.semester.id desc, s.course.code asc
            """)
    List<Enrollment> findForStudentHome(@Param("studentId") Long studentId);

    @Query("""
            SELECT
                u.id as studentId,
                u.username as studentUsername,
                u.fullName as fullName,
                u.email as email,
                s.submittedAt as submittedAt
            FROM Enrollment e
            JOIN e.student u
            LEFT JOIN Submission s
                ON s.submittedBy.id = u.id
                AND s.section.id = :sectionId
                AND s.kind = :kind
            WHERE e.section.id = :sectionId
            ORDER BY u.username
            """)
    List<StudentFeedbackStatus> adminStudentFeedbackStatus(
            Long sectionId,
            EvaluationKind kind);

    List<Enrollment> findBySection_Id(Long sectionId);

    @Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.section.semester.id = :semesterId")
    List<UserAccount> findDistinctStudentsBySemesterId(@Param("semesterId") Long semesterId);

}
