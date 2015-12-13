package com.salesforce.ant;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import com.sforce.soap.metadata.CodeCoverageWarning;
import com.sforce.soap.metadata.DeployDetails;
import com.sforce.soap.metadata.DeployMessage;
import com.sforce.soap.metadata.DeployResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.RunTestFailure;
import com.sforce.soap.metadata.RunTestsResult;
import com.sforce.ws.ConnectionException;

import io.imjohnmdaniel.tools.BatchTest;
import io.imjohnmdaniel.tools.XmlReport;

/**
 * Extension of the salesforce Ant task that converts the structured test result
 * data returned from a deploy into junitreport format XML so that it can be
 * consumed by tools such as Jenkin's JUnit report publisher.
 *
 * Also adds support for batchtest children for identifying test classes by name pattern.
 */
public class DeployWithXmlReportTask extends com.salesforce.ant.DeployTask
{

    private File junitreportdir;
    private List<BatchTest> batchTests = new ArrayList<BatchTest>();


    public File getJunitreportdir()
    {
        return junitreportdir;
    }

    public void setJunitreportdir(File junitreportdir)
    {
        this.junitreportdir = junitreportdir;
    }

    /**
     * Allows child BatchTest elements to be added that can identify tests by file name pattern.
     */
    public BatchTest createBatchTest()
    {
        BatchTest batchTest = new BatchTest(getProject());
        batchTests.add(batchTest);
        return batchTest;
    }

    /**
     * Returns any runtest items plus any batchtest items.
     */
    public String[] getRunTests()
    {

        List<String> names = new ArrayList<String>();
        names.addAll(Arrays.asList(super.getRunTests()));

        for (BatchTest batchTest : batchTests)
        {
            names.addAll(batchTest.getFilenames());
        }

        log("running tests: " + names, Project.MSG_VERBOSE);

        return names.toArray(new String[names.size()]);
    }

    /**
     * Necessary information already part of the response so grab it and format it.
     */
    public void handleResponse(MetadataConnection metadataConnection, StatusResult response)
            throws ConnectionException
    {

        if (junitreportdir != null)
        {

            DeployResult result = metadataConnection.checkDeployStatus(response.getId(), true);

            DeployDetails details = result.getDetails();

            RunTestsResult rtr = details.getRunTestResult();

            log("successes: " + rtr.getSuccesses().length, Project.MSG_VERBOSE);
            log("failures: " + rtr.getFailures().length, Project.MSG_VERBOSE);

            log("rtr: "+rtr);

            new XmlReport(junitreportdir).report(rtr);
        }

        try
        {
            // This sometimes throws an ArrayIndexOutOfBoundsException.
            // So when there is one fall back to a hopefully fixed version of the code.
            // If the method is ever fixed this workaround will just never get called.
            super.handleResponse(metadataConnection, response);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            fixedDecompiledHandleResponse(metadataConnection, response);
        }
    }

    private void fixedDecompiledHandleResponse(MetadataConnection metadataConnection, StatusResult response)
            throws ConnectionException
    {

        // Some code related to debug logging removed from here...

        DeployResult result = metadataConnection.checkDeployStatus(response.getId(), false);
        if (!result.isSuccess())
        {

            StringBuilder buf = new StringBuilder("Failures:\n");

            DeployDetails details = result.getDetails();

            DeployMessage messages[] = details.getComponentFailures();

            for (DeployMessage message : messages)
            {
                if (message.isSuccess())
                {
                    continue;
                }
                String loc = message.getLineNumber() != 0
                        ? "(" + message.getLineNumber() + "," + message.getColumnNumber()+ ")"
                        : "";

                if (loc.length() == 0 && !message.getFileName().equals(message.getFullName()))
                {
                    loc = "(" + message.getFullName() + ")";
                }

                buf.append(message.getFileName() + ":" + message.getProblem().toString() + "\n");
            }

            RunTestsResult rtr = details.getRunTestResult();

            if (rtr.getFailures() != null)
            {
                RunTestFailure[] failures = rtr.getFailures();

                for (RunTestFailure failure : failures)
                {
                    String n = (failure.getNamespace() != null ? failure.getNamespace() + "." : "") + failure.getName();

                    buf.append("Test failure, method: "
                            + n + "."
                            + failure.getMethodName()
                            + " -- "
                            + failure.getMessage()
                            + " stack "
                            + failure.getStackTrace()
                            + "\n\n");
                }

            }

            if (rtr.getCodeCoverageWarnings() != null)
            {
                CodeCoverageWarning[] ccws = rtr.getCodeCoverageWarnings();

                for (CodeCoverageWarning ccw : ccws)
                {
                    buf.append("Code coverage issue");

                    if (ccw.getName() != null)
                    {
                        String n = (ccw.getNamespace() != null ? ccw.getNamespace() + "." : "") + ccw.getName();
                        buf.append(", class: " + n);
                    }
                    buf.append(" -- " + ccw.getMessage() + "\n");
                }
            }
            throw new BuildException(buf.toString());
        }
        else
        {
            return;
        }
    }
}