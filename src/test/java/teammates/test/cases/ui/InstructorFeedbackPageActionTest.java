package teammates.test.cases.ui;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import teammates.common.Common;
import teammates.common.datatransfer.DataBundle;
import teammates.common.datatransfer.InstructorAttributes;
import teammates.common.datatransfer.StudentAttributes;
import teammates.logic.CoursesLogic;
import teammates.logic.FeedbackSessionsLogic;
import teammates.ui.controller.ControllerServlet;
import teammates.ui.controller.InstructorFeedbackPageAction;
import teammates.ui.controller.InstructorFeedbackPageData;
import teammates.ui.controller.ShowPageResult;

public class InstructorFeedbackPageActionTest extends BaseActionTest {

	DataBundle dataBundle;
	
	String unregUserId;
	String instructorId;
	String otherInstructorId;
	String studentId;
	String adminUserId;

	
	@BeforeClass
	public static void classSetUp() throws Exception {
		printTestClassHeader();
		URI = "/page/instructorFeedback";
		sr.registerServlet(URI, ControllerServlet.class.getName());
	}

	@BeforeMethod
	public void caseSetUp() throws Exception {
		dataBundle = getTypicalDataBundle();

		unregUserId = "unreg.user";
		
		InstructorAttributes instructor1OfCourse1 = dataBundle.instructors.get("instructor1OfCourse1");
		instructorId = instructor1OfCourse1.googleId;
		
		InstructorAttributes instructor1OfCourse2 = dataBundle.instructors.get("instructor1OfCourse2");
		otherInstructorId = instructor1OfCourse2.googleId;
		
		StudentAttributes student1InCourse1 = dataBundle.students.get("student1InCourse1");
		studentId = student1InCourse1.googleId;
		
		adminUserId = "admin.user";
		
		restoreTypicalDataInDatastore();
	}
	
	@Test
	public void testAccessControl() throws Exception{
		
		String[] submissionParams = new String[]{};
		
		logoutUser();
		verifyCannotAccess(submissionParams);
		verifyCannotMasquerade(addUserIdToParams(instructorId,submissionParams));
		
		loginUser(unregUserId);
		verifyCannotAccess(submissionParams);
		verifyCannotMasquerade(addUserIdToParams(instructorId,submissionParams));
		
		loginAsStudent(studentId);
		verifyCannotAccess(submissionParams);
		verifyCannotMasquerade(addUserIdToParams(instructorId,submissionParams));
		
		loginAsInstructor(instructorId);
		verifyCanAccess(submissionParams);
		verifyCannotMasquerade(addUserIdToParams(otherInstructorId,submissionParams));
		submissionParams = new String[]{Common.PARAM_COURSE_ID, "idOfTypicalCourse2"};
		verifyCannotAccess(submissionParams); //trying to create evaluation for someone else's course
		
		loginAsAdmin(adminUserId);
		//not checking for non-masquerade mode because admin may not be an instructor
		verifyCanMasquerade(addUserIdToParams(instructorId,submissionParams));
		
	}
	
	@Test
	public void testExecuteAndPostProcess() throws Exception{
		
		String[] submissionParams = new String[]{};
		
		InstructorAttributes instructor1ofCourse1 = dataBundle.instructors.get("instructor1OfCourse1");
		
		______TS("Typical case, 2 courses");
		
		CoursesLogic.inst().createCourseAndInstructor(instructorId, "new-course", "New course");
		loginAsInstructor(instructorId);
		InstructorFeedbackPageAction a = getAction(submissionParams);
		ShowPageResult r = (ShowPageResult)a.executeAndPostProcess();
		
		assertEquals(Common.JSP_INSTRUCTOR_FEEDBACK+"?error=false&user=idOfInstructor1OfCourse1", r.getDestinationWithParams());
		assertEquals(false, r.isError);
		assertEquals("", r.getStatusMessage());
		
		InstructorFeedbackPageData pageData = (InstructorFeedbackPageData)r.data;
		assertEquals(instructorId, pageData.account.googleId);
		assertEquals(2, pageData.courses.size());
		assertEquals(2, pageData.existingEvals.size());
		assertEquals(3, pageData.existingSessions.size());
		assertEquals(null, pageData.newFeedbackSession);
		assertEquals(null, pageData.courseIdForNewSession);
		
		String expectedLogMessage = "TEAMMATESLOG|||instructorFeedback|||instructorFeedback" +
				"|||true|||Instructor|||Instructor 1 of Course 1|||idOfInstructor1OfCourse1" +
				"|||instr1@course1.com|||Number of feedback sessions: 3|||/page/instructorFeedback";
		assertEquals(expectedLogMessage, a.getLogMessage());
		
		______TS("Masquerade mode, 0 sessions");
		
		FeedbackSessionsLogic.inst().deleteFeedbackSessionsForCourse(instructor1ofCourse1.courseId);
		
		loginAsAdmin(adminUserId);
		submissionParams = new String[]{Common.PARAM_COURSE_ID, instructor1ofCourse1.courseId};
		a = getAction(addUserIdToParams(instructorId, submissionParams));
		r = (ShowPageResult) a.executeAndPostProcess();
		
		assertEquals(
				Common.JSP_INSTRUCTOR_FEEDBACK+"?message=You+have+not+created+any+feedback+sessions+yet." +
						"+Use+the+form+above+to+create+a+new+feedback+session.&error=false&user=idOfInstructor1OfCourse1", 
				r.getDestinationWithParams());
		assertEquals("You have not created any feedback sessions yet. Use the form above to create a new feedback session.", 
				r.getStatusMessage());
		assertEquals(false, r.isError);
		
		pageData = (InstructorFeedbackPageData) r.data;
		assertEquals(instructorId, pageData.account.googleId);
		assertEquals(2, pageData.courses.size());
		assertEquals(2, pageData.existingEvals.size());
		assertEquals(0, pageData.existingSessions.size());
		assertEquals(null, pageData.newFeedbackSession);
		assertEquals(instructor1ofCourse1.courseId, pageData.courseIdForNewSession);
		
		expectedLogMessage = "TEAMMATESLOG|||instructorFeedback|||instructorFeedback" +
				"|||true|||Instructor(M)|||Instructor 1 of Course 1|||idOfInstructor1OfCourse1" +
				"|||instr1@course1.com|||Number of feedback sessions: 0|||/page/instructorFeedback";
		assertEquals(expectedLogMessage, a.getLogMessage());
		
		______TS("Masquerade mode, 0 courses");
		
		CoursesLogic.inst().deleteCourseCascade(instructor1ofCourse1.courseId);
		CoursesLogic.inst().deleteCourseCascade("new-course");
		
		submissionParams = new String[]{Common.PARAM_COURSE_ID, instructor1ofCourse1.courseId};
		a = getAction(addUserIdToParams(instructorId, submissionParams));
		r = (ShowPageResult) a.executeAndPostProcess();
		
		assertEquals(
				Common.JSP_INSTRUCTOR_FEEDBACK+"?message=You+have+not+created+any+courses+yet." +
						"+Go+%3Ca+href%3D%22%2Fpage%2FinstructorCourse%3Fuser%3DidOfInstructor1OfCourse1%22%3Ehere%3C%2Fa%3E+to+create+one.&error=false&user=idOfInstructor1OfCourse1", 
				r.getDestinationWithParams());
		assertEquals("You have not created any courses yet. Go <a href=\"/page/instructorCourse?user=idOfInstructor1OfCourse1\">here</a> to create one.", r.getStatusMessage());
		assertEquals(false, r.isError);
		
		pageData = (InstructorFeedbackPageData) r.data;
		assertEquals(instructorId, pageData.account.googleId);
		assertEquals(0, pageData.courses.size());
		assertEquals(0, pageData.existingEvals.size());
		assertEquals(0, pageData.existingSessions.size());
		assertEquals(null, pageData.newFeedbackSession);
		assertEquals(instructor1ofCourse1.courseId, pageData.courseIdForNewSession);
		
		expectedLogMessage = "TEAMMATESLOG|||instructorFeedback|||instructorFeedback" +
				"|||true|||Instructor(M)|||Instructor 1 of Course 1|||idOfInstructor1OfCourse1" +
				"|||instr1@course1.com|||Number of feedback sessions: 0|||/page/instructorFeedback";
		assertEquals(expectedLogMessage, a.getLogMessage());
	}
	
	
	private InstructorFeedbackPageAction getAction(String... params) throws Exception{
			return (InstructorFeedbackPageAction) (super.getActionObject(params));
	}

}