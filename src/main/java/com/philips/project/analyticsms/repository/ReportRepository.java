package com.philips.project.analyticsms.repository;

import com.philips.project.analyticsms.beans.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ReportRepository extends JpaRepository<Report , Integer> {
    public List<Report> findAll();
    public Report findById(int id);
    public Report findByDate(String date);

//    @Transactional
//    @Modifying
    @Query(value = "SELECT * FROM report WHERE date BETWEEN :startDate AND :endDate",nativeQuery = true)
    public List<Report> getReportsBetweenDatesQuery(@Param("startDate") String first,@Param("endDate") String second);


}
