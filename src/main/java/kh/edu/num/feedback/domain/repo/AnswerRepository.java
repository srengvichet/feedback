package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.Answer;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.QuestionType;
import kh.edu.num.feedback.web.admin.dto.AdminQuestionScoreStat;
import kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow;
import kh.edu.num.feedback.web.teacher.dto.QuestionStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

  // ===== common =====
  List<Answer> findBySubmission_Id(Long submissionId);

  void deleteBySubmission_Id(Long submissionId);

  // ===== TEACHER report (Student Feedback only) =====

  @Query("""
        select a.textValue
        from Answer a
          join a.question q
          join a.submission s
        where s.section.id = :sectionId
          and s.kind = kh.edu.num.feedback.domain.entity.EvaluationKind.STUDENT_FEEDBACK
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.TEXT
          and a.textValue is not null
          and trim(a.textValue) <> ''
        order by s.submittedAt desc
      """)
  List<String> commentsBySection(@Param("sectionId") Long sectionId);

  @Query("""
        select new kh.edu.num.feedback.web.teacher.dto.QuestionStat(
          q.id,
          q.text,
          avg(a.numericValue * 1.0),
          min(a.numericValue),
          max(a.numericValue),
          count(a.id)
        )
        from Answer a
          join a.question q
          join a.submission s
        where s.section.id = :sectionId
          and s.kind = kh.edu.num.feedback.domain.entity.EvaluationKind.STUDENT_FEEDBACK
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
          and a.numericValue is not null
        group by q.id, q.text, q.orderNo
        order by q.orderNo asc
      """)
  List<QuestionStat> ratingStatsBySection(@Param("sectionId") Long sectionId);

  @Query("""
        select avg(a.numericValue * 1.0)
        from Answer a
          join a.submission s
          join a.question q
        where s.section.id = :sectionId
          and s.kind = kh.edu.num.feedback.domain.entity.EvaluationKind.STUDENT_FEEDBACK
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
          and a.numericValue is not null
      """)
  Double overallAvgScoreBySection(@Param("sectionId") Long sectionId);

  // ===== ADMIN report (kind passed in, used for STUDENT_FEEDBACK now) =====

  @Query("""
      select new kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow(
        cs.id,
        sem.name,
        c.code,
        c.name,
        t.username,
        cs.shiftTime,
        cs.building,
        cs.room,
        cs.sectionName,

        avg(case when q.orderNo between 1 and 10 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 11 and 13 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 14 and 18 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 19 and 21 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 22 and 26 then (1.0 * a.numericValue) end),

        avg(1.0 * a.numericValue),
        count(distinct s.id),
        max(s.submittedAt)
      )
      from Answer a
      join a.submission s
      join s.semester sem
      join s.section cs
      join cs.course c
      join cs.teacher t
      join a.question q
      where s.kind = :kind
        and sem.id = :semesterId
        and q.kind = :kind
        and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
        and a.numericValue is not null
      group by cs.id, sem.name, c.code, c.name, t.username, cs.shiftTime, cs.building, cs.room, cs.sectionName
      order by t.username, c.code, cs.sectionName
      """)
  List<AdminSectionReportRow> adminSectionReport(@Param("semesterId") Long semesterId,
      @Param("kind") EvaluationKind kind);

  @Query("""
      select new kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow(
        t.id,
        sem.name,
        '',
        'Teacher Self-Assessment',
        case
          when t.fullName is null or trim(t.fullName) = '' then t.username
          else t.fullName
        end,
        null,
        null,
        null,
        null,

        avg(case when q.orderNo between 1 and 5 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 6 and 10 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 11 and 15 then (1.0 * a.numericValue) end),
        avg(case when q.orderNo between 16 and 20 then (1.0 * a.numericValue) end),
        null,

        avg(1.0 * a.numericValue),
        count(distinct s.id),
        max(s.submittedAt)
      )
      from Answer a
      join a.submission s
      join s.semester sem
      join s.submittedBy t
      join a.question q
      where s.kind = :kind
        and sem.id = :semesterId
        and q.kind = :kind
        and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
        and a.numericValue is not null
      group by t.id, sem.name, t.username, t.fullName
      order by case
          when t.fullName is null or trim(t.fullName) = '' then t.username
          else t.fullName
        end
      """)
  List<AdminSectionReportRow> adminTeacherSelfReport(@Param("semesterId") Long semesterId,
      @Param("kind") EvaluationKind kind);

  @Query("""
        select new kh.edu.num.feedback.web.admin.dto.AdminQuestionScoreStat(
          q.orderNo,
          q.text,
          avg(a.numericValue * 1.0),
          min(a.numericValue),
          max(a.numericValue),
          count(a.id)
        )
        from Answer a
          join a.submission s
          join a.question q
        where s.kind = :kind
          and s.section.id = :sectionId
          and q.kind = :kind
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
          and a.numericValue is not null
        group by q.orderNo, q.text
        order by q.orderNo asc
      """)
  List<AdminQuestionScoreStat> adminQuestionStats(@Param("sectionId") Long sectionId,
      @Param("kind") EvaluationKind kind);

  @Query("""
        select new kh.edu.num.feedback.web.admin.dto.AdminQuestionScoreStat(
          q.orderNo,
          q.text,
          avg(a.numericValue * 1.0),
          min(a.numericValue),
          max(a.numericValue),
          count(a.id)
        )
        from Answer a
          join a.submission s
          join a.question q
        where s.kind = :kind
          and s.semester.id = :semesterId
          and s.section is null
          and s.submittedBy.id = :teacherId
          and q.kind = :kind
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.RATING
          and a.numericValue is not null
        group by q.orderNo, q.text
        order by q.orderNo asc
      """)
  List<AdminQuestionScoreStat> adminTeacherSelfQuestionStats(@Param("semesterId") Long semesterId,
      @Param("teacherId") Long teacherId,
      @Param("kind") EvaluationKind kind);

  @Query("""
        select a.textValue
        from Answer a
          join a.submission s
          join a.question q
        where s.kind = :kind
          and s.section.id = :sectionId
          and q.kind = :kind
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.TEXT
          and a.textValue is not null
          and trim(a.textValue) <> ''
        order by s.submittedAt desc
      """)
  List<String> adminComments(@Param("sectionId") Long sectionId,
      @Param("kind") EvaluationKind kind);

  @Query("""
        select a.textValue
        from Answer a
          join a.submission s
          join a.question q
        where s.kind = :kind
          and s.semester.id = :semesterId
          and s.section is null
          and s.submittedBy.id = :teacherId
          and q.kind = :kind
          and q.type = kh.edu.num.feedback.domain.entity.QuestionType.TEXT
          and a.textValue is not null
          and trim(a.textValue) <> ''
        order by s.submittedAt desc
      """)
  List<String> adminTeacherSelfComments(@Param("semesterId") Long semesterId,
      @Param("teacherId") Long teacherId,
      @Param("kind") EvaluationKind kind);
}
