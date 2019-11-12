package com.github.billyjulius.serenityjbehavetestlink;

import net.serenitybdd.jbehave.SerenityStory;
import net.thucydides.core.model.*;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.util.SystemEnvironmentVariables;
import org.jbehave.core.annotations.AfterScenario;
import org.jbehave.core.annotations.ScenarioType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.billyjulius.testlinkhelper.StepResult;
import com.github.billyjulius.testlinkhelper.TestLinkMain;

public class Base extends SerenityStory {

    protected TestOutcome testOutcome;
    protected String errorMessage = "";
    protected Boolean isSuccess = true;
    protected List<String> exampleValues = new ArrayList<String>();
    protected List<StepResult> stepResults =  new ArrayList<StepResult>();

    String TESTLINK_URL;
    String TESTLINK_KEY;

    String _PROJECTNAME;
    Integer _SUITEID;
    Integer _PROJECTID;
    Integer _VERSION;
    String _BUILDNAME;
    String _PLANNAME;
    String _USERNAME;
    String _TCSUMMARY = "";

    public Base() {
        EnvironmentVariables variables = SystemEnvironmentVariables.createEnvironmentVariables();
        this.TESTLINK_URL = variables.getProperty("testlink.url");
        this.TESTLINK_KEY = variables.getProperty("testlink.key");
        this._PROJECTID = Integer.parseInt(variables.getProperty("testlink.project.id"));
        this._PROJECTNAME = variables.getProperty("testlink.project.name");
        this._VERSION = variables.getPropertyAsInteger("testlink.project.version", 1);
        this._SUITEID = Integer.parseInt(variables.getProperty("testlink.project.suite.id"));
        this._BUILDNAME = variables.getProperty("testlink.build.name");
        this._PLANNAME = variables.getProperty("testlink.plan.name");
        this._USERNAME = variables.getProperty("testlink.username");
    }

    @AfterScenario(uponType = ScenarioType.NORMAL)
    public void TestLinkIntegration() {
        this.testOutcome = GetTestOutcome();

        String TestCaseName =  this.testOutcome.getName();

        //  Suite ID from scenario name with pattern '1234567'
        this._SUITEID = ParseSuiteID(TestCaseName);

        GetTestResult();
        GetTestErrorMessage();

        TestLinkMain testLinkMain = new TestLinkMain(TESTLINK_URL, TESTLINK_KEY);
        testLinkMain.Init(_PROJECTNAME, _PROJECTID, _VERSION, _BUILDNAME, _PLANNAME, _USERNAME);
        if(stepResults.size() > 0) {
            testLinkMain.Run(TestCaseName, _TCSUMMARY, this.isSuccess, this.errorMessage, this.stepResults, this._SUITEID);
        }
    }

    @AfterScenario(uponType = ScenarioType.EXAMPLE)
    public void TestLinkIntegrationforExample() {
        this.testOutcome = GetTestOutcome();

        String TestCaseName =  this.testOutcome.getName();

        //  Suite ID from scenario name with pattern '1234567'
        this._SUITEID = ParseSuiteID(TestCaseName);

        GetTestResultWithExample();
        GetTestErrorMessage();

        TestLinkMain testLinkMain = new TestLinkMain(TESTLINK_URL, TESTLINK_KEY);
        testLinkMain.Init(_PROJECTNAME, _PROJECTID, _VERSION, _BUILDNAME, _PLANNAME, _USERNAME);
        if(stepResults.size() > 0) {
            testLinkMain.Run(TestCaseName, _TCSUMMARY, this.isSuccess, this.errorMessage, this.stepResults, this._SUITEID);
        }
    }

    private void GetTestResultWithExample() {
        // Should contain string if scenario examples
        String outline = this.testOutcome.getDataDrivenSampleScenario();

        List<TestStep> testStepList = this.testOutcome.getTestSteps();

        stepResults.clear();
        String[] outline_arr = outline.split("\n");

        TestStep lastStep = testStepList.get(testStepList.size()-1);
        List<TestStep> childrenSteps = lastStep.getChildren();

        for(Integer i=0; i<outline_arr.length; i++) {
            StepResult stepResult = new StepResult();
            stepResult.name = outline_arr[i];
            stepResult.status = childrenSteps.get(i).isSuccessful() ? "Success" : "Failed";
            stepResults.add(stepResult);
        }

        DataTable dataTable = this.testOutcome.getDataTable();
        List<String> exampleFields = dataTable.getHeaders();
        List<DataTableRow> exampleValuesRow = dataTable.getRows();

        if (this.exampleValues.size() == 0) {
            for (DataTableRow dataTableRow : exampleValuesRow) {
                List<String> values_temp = dataTableRow.getStringValues();
                for (String value : values_temp) {
                    this.exampleValues.add(value.toString());
                }
            }
        }

        if(_TCSUMMARY == "") {
            _TCSUMMARY += "fields = " + String.join(",", exampleFields) + "\n";
            _TCSUMMARY += "values = " + String.join(",", this.exampleValues) + "\n";
        }

        this.errorMessage = lastStep.getDescription() + System.lineSeparator() + System.lineSeparator();
    }

    private void GetTestResult() {
        _TCSUMMARY = this.testOutcome.getName();
        List<TestStep> testStepList = this.testOutcome.getTestSteps();

        for(TestStep testStep : testStepList) {
            Pattern pattern = Pattern.compile("^(Given|When|Then)");
            Matcher matcher = pattern.matcher(testStep.getDescription());
            Boolean storyStep = matcher.find();

            if(testStep.getChildren().size() > 0 && !storyStep) {
                stepResults.clear();
                addStepResult(testStep);
            }

            if(testStep.getChildren().size() == 0) {
                addStepResult(testStep);
            }

            if(testStep.getChildren().size() > 0 && storyStep) {
                // If using rest-assured ignore childern
                addStepResult(testStep);

                List<TestStep> childrenSteps = testStep.getChildren();
                for (TestStep testStep1 : childrenSteps) {
                    Boolean childStoryStep = pattern.matcher(testStep1.getDescription()).find();
                    if(childStoryStep) {
                        addStepResult(testStep1);
                    }
                }
            }
        }
    }

    private Integer ParseSuiteID(String TestCaseName) {
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(TestCaseName);

        if(matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private TestOutcome GetTestOutcome() {
        List<TestOutcome> testOutcomeList= StepEventBus.getEventBus().getBaseStepListener().getTestOutcomes();

        TestOutcome testOutcome = testOutcomeList.get(testOutcomeList.size()-1);

        return testOutcome;
    }

    private void GetTestErrorMessage() {
        if(testOutcome.isFailure() || testOutcome.isError()) {
            errorMessage += testOutcome.getErrorMessage();
            isSuccess = false;
        }
    }

    private void addStepResult(TestStep testStep) {
        StepResult stepResult = new StepResult();
        stepResult.name = testStep.getDescription();
        stepResult.status = testStep.isSuccessful() ? "Success" : "Failed";
        stepResults.add(stepResult);
    }

    private void addStepResult(TestStep testStep, String description) {
        StepResult stepResult = new StepResult();
        stepResult.name = description;
        stepResult.status = testStep.isSuccessful() ? "Success" : "Failed";
        stepResults.add(stepResult);
    }
}

