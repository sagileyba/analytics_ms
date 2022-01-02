package com.philips.project.analyticsms.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philips.project.analyticsms.beans.Report;
import com.philips.project.analyticsms.repository.ReportRepository;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;


    //fet all reports
    public List<Report> getReports() {
        return reportRepository.findAll();
    }

    //Get report by date
    public Report getReportByDate(String date) {
        return reportRepository.findByDate(date);
    }

    //Add new report
    public void addReport(Report report) {
        reportRepository.save(report);
    }

    //Update the report params and re-calculate the number of patients
    //
    public void updateTodayReport(Report report) {
        Report reportToUpdate = report;
        reportToUpdate.setPatients(report.getPatients());
        reportRepository.save(reportToUpdate);
    }

//    public int calculateReport(Person[] reports){
//
//    }
    //Get prediction reports between dates (must be in the analytics DB to be shown)
    public  List<Report> getPredictionBetweenDatesReport(String endDate ,String startDate) {   // range of prediction ... need to add another date
    	
    //	String startDate  = LocalDate.parse(endDate).minusDays(14).toString();  // currently checking 15 days
    	List<Report> reports = reportRepository.getReportsBetweenDatesQuery(startDate,endDate);
    	for(Report report : reports) {
    		System.out.println(report);
    	}
//        return createJson(reports);
        return reports;
    }
    

    public void autoRecieveData(String date, int positives, int numberOfPCRs,int central, int north,int south ) {  // when db inserted to msdb, this function will be called
        Report newReport =  this.reportRepository.findByDate(date);
    	if (newReport == null) {
        	newReport = new Report();
        }
    	newReport.setPatients(numberOfPCRs);
    	newReport.setDate(date);    			
    	if(numberOfPCRs-positives != 0) {
            newReport.setPositiveRatio((double)positives/(numberOfPCRs-positives));
    	}
    	newReport.setCenterCount( central);
    	newReport.setNorthCount( north);
    	newReport.setSouthCount( south);

        newReport.setPositivePCR(positives);        
        reportRepository.save(newReport);
    
        newReport.setAccumPositives(calculateHelper(date).getAccumPositives());
        reportRepository.save(newReport);

    }
    public  Report calculateDailyReport(String date) {
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	    LocalDate oneDayAgo = LocalDate.parse(date,formatter).minusDays(1);
	    LocalDate twoWeeksAgo = LocalDate.parse(date,formatter).minusDays(15);
	    Report yesterdayDate =  this.reportRepository.findByDate(oneDayAgo.toString());
	   
	    LocalDate runOnDayes =twoWeeksAgo;
	   
	    Report runOnDayesReport = this.reportRepository.findByDate(runOnDayes.toString());
	    
	    while( yesterdayDate   == null && runOnDayesReport != null) {
	    	runOnDayes = runOnDayes.plusDays(1);
		    runOnDayesReport =  this.reportRepository.findByDate(runOnDayes.toString());
	    }
	    
	    while(yesterdayDate   == null ) {
	    	calculateHelper(runOnDayes.toString());
		    yesterdayDate =  this.reportRepository.findByDate(oneDayAgo.toString());
		    runOnDayes = runOnDayes.plusDays(1);
	    }
	    
	    return calculateHelper(date);
    }

    
    /**
     * This function create new daily report
     * @param date
     * F()= TODAY.POSITIVES + R[date.minusDays(1)].positives
     * -0.8*(R[date.minusDays(7)].positives-R[date.minusDays(8)])
     * -0.2*(R[date.minusDays(14)]-R[date.minusDays(15)])
     */
    private  Report calculateHelper(String date) {    //  calculate prediction algorithm as assumed 
    	
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate oneWeekAgo = LocalDate.parse(date,formatter).minusDays(7);
        LocalDate twoWeeksAgo = LocalDate.parse(date,formatter).minusDays(14);
        LocalDate oneDayAgo = LocalDate.parse(date,formatter).minusDays(1);


        //Test
   //     System.out.println("yesterday: " + oneDayAgo);
   //     System.out.println("twoWeeksAgo: " + twoWeeksAgo);
        
        
        Report currDateReport =  this.reportRepository.findByDate(date);
        Report yesterdayDate =  this.reportRepository.findByDate(oneDayAgo.toString());
        Report day7Report = this.reportRepository.findByDate(oneWeekAgo.toString());
        Report day14Report = reportRepository.findByDate(twoWeeksAgo.toString());      
        

        if(currDateReport  == null) {
        	currDateReport = new Report();
        	currDateReport.setDate(date);
        }
        else if (currDateReport != null && currDateReport.getAccumPositives()>0) {
        	return currDateReport;
        }
        
        
        int accumYesterday =   yesterdayDate   == null?    0:yesterdayDate.getAccumPositives();      

        int todayPositive  =   currDateReport  == null?    0:currDateReport.getPositivePCR();
        int sevenAgo       =   day7Report      == null?	 0:day7Report.getPositivePCR();
        int fourTeenAgo    =   day14Report     == null?    0:day14Report.getPositivePCR();

        int less80Percent = (int)(0.8*sevenAgo);
        int less20Percent = (int) (fourTeenAgo*0.2);
        
       
        
        int sum =(int) (todayPositive + accumYesterday - less80Percent  - less20Percent)    ;
        int accumNorthYesterday =   yesterdayDate   == null?    0:yesterdayDate.getNorthCount();      
        int accumSouthYesterday =   yesterdayDate   == null?    0:yesterdayDate.getSouthCount();      
        int accumCenterYesterday =   yesterdayDate   == null?    0:yesterdayDate.getCenterCount();      

        currDateReport.setCenterCount( currDateReport.getCenterCount() + accumCenterYesterday );
        currDateReport.setNorthCount(  currDateReport.getNorthCount() + accumNorthYesterday);
    	currDateReport.setSouthCount( currDateReport.getSouthCount() + accumSouthYesterday);
      
        int toReduce =  less80Percent  + less20Percent;
        if( toReduce > 0)
        	subscribe(currDateReport, toReduce );  
    	
        
        currDateReport.setAccumPositives(sum);

        reportRepository.save(currDateReport);
        return currDateReport;
    }
    private void subscribe(Report currDateReport ,int toReduce ) {  // reduce area of patient when calculateHelper run
    	int reducNorth=0 , reduceCenter=0, reduceSouth=0;
    	
    	reduceSouth = toReduce/3;
    	reducNorth = toReduce/3;
    	reduceCenter = toReduce - reducNorth - reduceSouth;
    	if(reduceCenter<0){
    		reduceCenter = 0;
        }
        if(currDateReport.getNorthCount() - reducNorth > 0)
            currDateReport.setNorthCount(currDateReport.getNorthCount() - reducNorth);
        if(currDateReport.getSouthCount() - reduceSouth > 0)
            currDateReport.setSouthCount(currDateReport.getSouthCount() - reduceSouth);
        if(currDateReport.getCenterCount() - reduceCenter > 0)
		currDateReport.setCenterCount(currDateReport.getCenterCount() - reduceCenter);


    	
    }
/*    private void subscribe(Report currDateReport ,int toReduce ,int randomArea) {

     	int negativeNum;
    	switch(randomArea) {
    	case 0:
        	if( currDateReport.getNorthCount()  < toReduce ) {
        		currDateReport.setNorthCount(0);
        		negativeNum = (currDateReport.getNorthCount() - toReduce)*-1;
        		if(currDateReport.getCenterCount() > negativeNum) {
        			subscribe(currDateReport, negativeNum , 2);
        		}
        		else {
        			subscribe(currDateReport, negativeNum, 1);
        		}
        	} 	
        	else
        		currDateReport.setNorthCount(currDateReport.getNorthCount() - toReduce);
    		break;
    	case 1:    		
    		if( currDateReport.getSouthCount()  < toReduce ) {
        		currDateReport.setSouthCount(0);
        		negativeNum = (currDateReport.getSouthCount() - toReduce)*-1;
        		if(currDateReport.getNorthCount() > negativeNum) {
        			subscribe(currDateReport, negativeNum , 0);
        		}
        		else {
        			subscribe(currDateReport, negativeNum, 2);
        		}
        	} 	
        	else
        		currDateReport.setSouthCount(currDateReport.getSouthCount() - toReduce);
    		break;
    	case 2:    
    		if( currDateReport.getCenterCount()  < toReduce ) {
        		currDateReport.setCenterCount(0);
        		negativeNum = (currDateReport.getCenterCount() - toReduce)*-1;
        		if(currDateReport.getSouthCount() > negativeNum) {
        			subscribe(currDateReport, negativeNum , 1);
        		}
        		else {
        			subscribe(currDateReport, negativeNum, 0);
        		}
        	} 	
        	else
        		currDateReport.setCenterCount(currDateReport.getCenterCount() - toReduce);
    		break;
    	}
    }
*/
    
    private String createJson(Report report) throws JsonProcessingException {
    	 ObjectMapper mapper = new ObjectMapper();
  	    String json = mapper.writeValueAsString(report); 
      	return json;
    }
    
    private String createJson(List<Report> reports) throws JsonProcessingException {
   	 ObjectMapper mapper = new ObjectMapper();
 	    String json = mapper.writeValueAsString(reports); 
     	return json;
   }
    
    

}
