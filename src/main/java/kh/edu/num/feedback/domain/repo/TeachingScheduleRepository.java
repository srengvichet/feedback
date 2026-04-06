package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.ShiftTime;
import kh.edu.num.feedback.domain.entity.TeachingSchedule;
import kh.edu.num.feedback.domain.entity.Weekday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeachingScheduleRepository extends JpaRepository<TeachingSchedule, Long> {

  // @Query("""
  // select t from TeachingSchedule t
  // where t.semester.id = :semesterId
  // and t.shiftTime = :shiftTime
  // and ((:cohortId is null and t.cohort is null) or (t.cohort.id = :cohortId))
  // and ((:groupNo is null and t.groupNo is null) or (t.groupNo = :groupNo))
  // order by t.subjectNo asc, t.weekday asc
  // """)
  // List<TeachingSchedule> findForStudent(@Param("semesterId") Long semesterId,
  // @Param("cohortId") Long cohortId,
  // @Param("groupNo") Integer groupNo,
  // @Param("shiftTime") ShiftTime shiftTime);
  @Query("""
          select t from TeachingSchedule t
          left join t.classSection cs
          where
            (
              (cs is not null and cs.semester.id = :semesterId)
              or
              (cs is null and t.semester.id = :semesterId)
            )
            and
            (
              (cs is not null and cs.shiftTime = :shiftTime)
              or
              (cs is null and t.shiftTime = :shiftTime)
            )
            and
            (
              (:cohortId is null)
              or
              (cs is not null and cs.cohort.id = :cohortId)
              or
              (cs is null and t.cohort.id = :cohortId)
            )
            and
            (
              (:groupNo is null)
              or
              (cs is not null and cs.groupNo = :groupNo)
              or
              (cs is null and t.groupNo = :groupNo)
            )
          order by t.subjectNo asc, t.weekday asc
      """)
  List<TeachingSchedule> findForStudent(
      @Param("semesterId") Long semesterId,
      @Param("cohortId") Long cohortId,
      @Param("groupNo") Integer groupNo,
      @Param("shiftTime") ShiftTime shiftTime);

  @Query("""
          select t from TeachingSchedule t
          left join t.classSection cs
          where
            (
              (cs is not null and cs.semester.id = :semesterId)
              or
              (cs is null and t.semester.id = :semesterId)
            )
            and
            (
              (cs is not null and cs.shiftTime = :shiftTime)
              or
              (cs is null and t.shiftTime = :shiftTime)
            )
            and
            t.weekday = :weekday
            and
            (
              (:cohortId is null)
              or
              (cs is not null and cs.cohort.id = :cohortId)
              or
              (cs is null and t.cohort.id = :cohortId)
            )
            and
            (
              (:groupNo is null)
              or
              (cs is not null and cs.groupNo = :groupNo)
              or
              (cs is null and t.groupNo = :groupNo)
            )
          order by t.subjectNo asc
      """)
  List<TeachingSchedule> findForStudentToday(
      @Param("semesterId") Long semesterId,
      @Param("cohortId") Long cohortId,
      @Param("groupNo") Integer groupNo,
      @Param("shiftTime") ShiftTime shiftTime,
      @Param("weekday") Weekday weekday);

  // ====== Distinct options for Join Code screen ======

  @Query("""
          select distinct
            case
              when cs is not null then cs.cohort.id
              else t.cohort.id
            end
          from TeachingSchedule t
          left join t.classSection cs
          where
            (
              (cs is not null and cs.semester.id = :semesterId)
              or
              (cs is null and t.semester.id = :semesterId)
            )
          order by 1 asc
      """)
  List<Long> distinctCohortIds(@Param("semesterId") Long semesterId);

  @Query("""
          select distinct
            case
              when cs is not null then cs.groupNo
              else t.groupNo
            end
          from TeachingSchedule t
          left join t.classSection cs
          where
            (
              (cs is not null and cs.semester.id = :semesterId)
              or
              (cs is null and t.semester.id = :semesterId)
            )
            and
            (
              (:cohortId is null)
              or
              (cs is not null and cs.cohort.id = :cohortId)
              or
              (cs is null and t.cohort.id = :cohortId)
            )
          order by 1 asc
      """)
  List<Integer> distinctGroupNos(
      @Param("semesterId") Long semesterId,
      @Param("cohortId") Long cohortId);

  @Query("""
    select distinct
      case
        when cs is not null then cs.shiftTime
        else t.shiftTime
      end
    from TeachingSchedule t
    left join t.classSection cs
    where
      (
        (cs is not null and cs.semester.id = :semesterId)
        or
        (cs is null and t.semester.id = :semesterId)
      )
      and
      (
        (:cohortId is null)
        or
        (cs is not null and cs.cohort.id = :cohortId)
        or
        (cs is null and t.cohort.id = :cohortId)
      )
      and
      (
        (:groupNo is null)
        or
        (cs is not null and cs.groupNo = :groupNo)
        or
        (cs is null and t.groupNo = :groupNo)
      )
    order by 1 asc
""")
List<ShiftTime> distinctShiftTimes(
    @Param("semesterId") Long semesterId,
    @Param("cohortId") Long cohortId,
    @Param("groupNo") Integer groupNo);

  @Query("""
      select t from TeachingSchedule t
      left join fetch t.classSection cs
      left join fetch cs.cohort
      left join fetch cs.semester
      left join fetch t.course
      left join fetch t.teacher
      """)
  List<TeachingSchedule> findAllWithSection();

}
