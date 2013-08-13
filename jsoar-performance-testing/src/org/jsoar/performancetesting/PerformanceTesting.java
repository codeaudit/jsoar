/**
 * 
 */
package org.jsoar.performancetesting;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import org.jsoar.kernel.SoarException;
import org.jsoar.performancetesting.Configuration.InvalidTestNameException;
import org.jsoar.performancetesting.Configuration.MalformedTestCategory;
import org.jsoar.performancetesting.Configuration.UnknownPropertyException;
import org.jsoar.performancetesting.csoar.CSoarTestFactory;
import org.jsoar.performancetesting.jsoar.JSoarTestFactory;
import org.jsoar.util.commands.OptionProcessor;

import com.google.common.collect.Lists;

/**
 * @author ALT
 * 
 */
public class PerformanceTesting
{
    private static final String SUMMARY_FILE_NAME = "test-summaries.txt";

    private static enum Options
    {
        help, directory, recursive, configuration, test, output, warmup, kind, jsoar, soar, runSeperateJVMs
    };

    private final PrintWriter out;

    private CSoarTestFactory csoarTestFactory;
    private final List<TestCategory> csoarTestCategories;
    private final List<Test> csoarTests;

    private JSoarTestFactory jsoarTestFactory;
    private final List<TestCategory> jsoarTestCategories;
    private final List<Test> jsoarTests;

    private Long seed = 123456789L;
    
    private int runCount = 1;
    private int warmUpCount = 10;

    private boolean jsoarEnabled = true;
    private boolean csoarEnabled = false;
    
    private List<String> categoriesToRun;
    private List<String> testsToRun;
    
    private String csvDirectory = "";
    
    private String testCategory = "";
    
    private boolean runTestsInSeparateJVMs = true;

    private static final int NON_EXIT = 1024;
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 255;
    private static final int EXIT_FAILURE_TEST = 254;
    private static final int EXIT_FAILURE_CONFIGURATION = 253;

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        final PrintWriter writer = new PrintWriter(System.out);
        final PerformanceTesting performanceTester = new PerformanceTesting(writer);

        final int result = performanceTester.doPerformanceTesting(args);

        writer.flush();

        if (result != EXIT_SUCCESS)
        {
            System.exit(result);
        }
    }

    /**
     * 
     * @param out
     */
    public PerformanceTesting(PrintWriter out)
    {
        this.out = out;

        this.jsoarTestCategories = new ArrayList<TestCategory>();
        this.jsoarTests = new ArrayList<Test>();

        this.csoarTestCategories = new ArrayList<TestCategory>();
        this.csoarTests = new ArrayList<Test>();

        this.jsoarTestFactory = new JSoarTestFactory();
        this.csoarTestFactory = new CSoarTestFactory();

        this.jsoarTestCategories.add(new TestCategory("Uncategorized Tests", new ArrayList<Test>()));
    }

    /**
     * Outputs to the PrintWriter the usage string.
     */
    public void usage()
    {
        out.println("Performance Testing - Performance testing framework for JSoar and CSoar\n" +
                    "performance-testing [options]\n" +
                    "\n" +
                    "Options:\n" +
                    "   -h, --help              This message.\n" +
                    "   -d, --directory         Load all tests (.soar files) from this directory recursively.\n" +
                    "   -c, --configuration     Load a configuration file to use for testing.\n" +
                    "   -t, --test              Manually specify a test to load.\n" +
                    "   -o, --output            The directory for all the CSV test results.\n" +
                    "   -w, --warmup            Specify the number of warm up runs for JSoar.\n" +
                    "   -k, --kind              Specify the test category.\n" +
                    "   -j, --jsoar             Run the tests in JSoar.\n" +
                    "   -s, --soar              Run the tests in CSoar.\n" +
                    "   -r, --runSeperateJVMs   Whether to run the tests in seperate jvms or not" +
                    "\n" +
                    "Note: When running with CSoar, CSoar's bin directory must be on the system\n" +
                    "      path or in java.library.path or specified in a configuration directory.\n");
    }
    
    private int parseConfiguration(OptionProcessor<Options> options)
    {
    	String configurationPath = options.get(Options.configuration);

        if (!configurationPath.endsWith(".properties"))
        {
            out.println("Configuration files need to be properties files.");
            return EXIT_FAILURE_CONFIGURATION;
        }

        Configuration config = new Configuration(configurationPath);
        int result = Configuration.PARSE_FAILURE;

        //Make sure there are no duplicate keys and then parse the properties file
        try
        {
            config.checkPropertiesFile(out);
            
            result = config.parse();
        }
        catch (IOException | UnknownPropertyException | InvalidTestNameException | MalformedTestCategory e)
        {
            out.println(e.getMessage());
            return EXIT_FAILURE;
        }

        if (result != Configuration.PARSE_SUCCESS)
        {
            out.println("Configuration parsing failed!");
            return EXIT_FAILURE_CONFIGURATION;
        }
        
        seed = config.getSeed();

        if (config.getRunCount() > 0)
            runCount = config.getRunCount();
        else
            out.println("Defaulting to running tests 20 times");

        if (config.getWarmUpCount() >= 0)
            warmUpCount = config.getWarmUpCount();
        else
            out.println("Defaulting to warming up tests 10 times");

        jsoarEnabled = config.getJSoarEnabled();
        csoarEnabled = config.getCSoarEnabled();
        
        runTestsInSeparateJVMs = config.getRunTestsInSeparateJVMs();

        if (!jsoarEnabled && !csoarEnabled)
        {
            out.println("WARNING: You must select something to run.  Defaulting to JSoar.");
            jsoarEnabled = true;
        }
        
        categoriesToRun = config.getCategoriesToRun();
        testsToRun = config.getTestsToRun();

        String csoarDirectory = config.getCSoarDirectory();
        String csoarLabel = config.getCSoarLabel();
        
        csvDirectory = config.getCSVDirectory().trim();

        csoarTestFactory.setLabel(csoarLabel);
        csoarTestFactory.setCSoarDirectory(csoarDirectory);

        this.csoarTestCategories.add(new TestCategory("Uncategorized Tests", new ArrayList<Test>()));

        SortedSet<Configuration.ConfigurationTest> configurationTests = config.getConfigurationTests();

        //Convert all the ConfigurationTest holders to actual tests.
        for (Configuration.ConfigurationTest test : configurationTests)
        {
            if (jsoarEnabled)
            {
                TestCategory jsoarCategory = TestCategory.getTestCategory(test.getTestCategory(), jsoarTestCategories);

                if (jsoarCategory == null)
                {
                    jsoarCategory = new TestCategory(test.getTestCategory(), new ArrayList<Test>());

                    jsoarTestCategories.add(jsoarCategory);
                }

                Test jsoarTest = jsoarTestFactory.createTest(test.getTestName(), test.getTestFile(), config.getDecisionCyclesToRunTest(test.getTestName()));
                jsoarTests.add(jsoarTest);

                jsoarCategory.addTest(jsoarTest);
            }

            if (csoarEnabled)
            {
                TestCategory csoarCategory = TestCategory.getTestCategory(test.getTestCategory(), csoarTestCategories);

                if (csoarCategory == null)
                {
                    csoarCategory = new TestCategory(test.getTestCategory(), new ArrayList<Test>());

                    csoarTestCategories.add(csoarCategory);
                }

                Test csoarTest = csoarTestFactory.createTest(test.getTestName(), test.getTestFile(), config.getDecisionCyclesToRunTest(test.getTestName()));
                csoarTests.add(csoarTest);

                csoarCategory.addTest(csoarTest);
            }
        }
        
        return NON_EXIT;
    }
    
    private int parseOptions(String[] args)
    {
    	//This is the same options processor for JSoar and so has the same limitations.
        final OptionProcessor<Options> options = new OptionProcessor<Options>();
        options.newOption(Options.help)
               .newOption(Options.directory).requiredArg()
               .newOption(Options.recursive)
               .newOption(Options.configuration).requiredArg()
               .newOption(Options.test).requiredArg()
               .newOption(Options.jsoar)
               .newOption(Options.kind).requiredArg()
               .newOption(Options.output).requiredArg()
               .newOption(Options.soar)
               .newOption(Options.warmup).requiredArg()
               .newOption(Options.runSeperateJVMs).requiredArg()
               .done();

        try
        {
            options.process(Lists.asList("PerformanceTesting", args));
        }
        catch (SoarException e)
        {
            out.println(e.getMessage());
            usage();
            return EXIT_SUCCESS;
        }

        if (options.has(Options.help))
        {
            usage();
            return EXIT_SUCCESS;
        }
        
        if (options.has(Options.output))
        {
            csvDirectory = options.get(Options.output);
        }
        
        if (options.has(Options.warmup))
        {
            warmUpCount = Integer.parseInt(options.get(Options.warmup));
        }
        
        if (options.has(Options.kind))
        {
            testCategory = options.get(Options.kind);
            
            jsoarTestCategories.add(new TestCategory(testCategory, new ArrayList<Test>()));
            csoarTestCategories.add(new TestCategory(testCategory, new ArrayList<Test>()));
        }
        else
        {
            testCategory = "Uncategorized Tests";
        }
        
        if (options.has(Options.jsoar))
        {
            jsoarEnabled = true;
        }
        
        if (options.has(Options.soar))
        {
            csoarEnabled = true;
        }

        //This will load all tests from a directory into the uncategorized tests category.
        if (options.has(Options.directory))
        {
            String directory = options.get(Options.directory);

            Path directoryPath = FileSystems.getDefault().getPath(directory);
            DirectoryStream<Path> stream;

            try
            {
                stream = Files.newDirectoryStream(directoryPath);
            }
            catch (IOException e)
            {
                out.println("Failed to create new directory stream: " + e.getMessage());
                return EXIT_FAILURE;
            }

            for (Path path : stream)
            {
                String testName = path.getFileName().toString();

                if (!testName.endsWith(".soar"))
                    continue;

                testName = new File(testName).getName();

                if (jsoarEnabled)
                {
                    Test jsoarTest = jsoarTestFactory.createTest(testName, path.toString(), 0);
                    jsoarTests.add(jsoarTest);

                    TestCategory.getTestCategory(testCategory, jsoarTestCategories).addTest(jsoarTest);
                }

                if (csoarEnabled)
                {
                    Test csoarTest = csoarTestFactory.createTest(testName, path.toString(), 0);
                    csoarTests.add(csoarTest);

                    TestCategory.getTestCategory(testCategory, csoarTestCategories).addTest(csoarTest);
                }
            }

            try
            {
                stream.close();
            }
            catch (IOException e)
            {
                out.println("Failed to close directory stream: " + e.getMessage());
                return EXIT_FAILURE;
            }
        }

        // This will load an individual test into the uncategorized tests category, only really useful
        // for single tests that you don't want to create a configuration file for
        if (options.has(Options.test))
        {
            String testPath = options.get(Options.test);

            if (!testPath.endsWith(".soar"))
            {
                out.println("Tests need to end with .soar");
                return EXIT_FAILURE_TEST;
            }

            String testName = new File(testPath).getName();;

            if (jsoarEnabled)
            {
                Test jsoarTest = jsoarTestFactory.createTest(testName, testPath, 0);
                jsoarTests.add(jsoarTest);

                TestCategory.getTestCategory(testCategory, jsoarTestCategories).addTest(jsoarTest);
            }

            if (csoarEnabled)
            {
                Test csoarTest = csoarTestFactory.createTest(testName, testPath, 0);
                csoarTests.add(csoarTest);

                TestCategory.getTestCategory(testCategory, csoarTestCategories).addTest(csoarTest);
            }
        }
        
        if (options.has(Options.runSeperateJVMs))
        {
            runTestsInSeparateJVMs = Boolean.parseBoolean(options.get(Options.runSeperateJVMs));
        }
        
        if (options.has(Options.configuration))
        {
            parseConfiguration(options);
        }
        
        return NON_EXIT;
    }
    
    /**
     * 
     * @param testRunners All the tests
     * @param testCategories All the test categories
     * @return Whether running the tests was successful or not
     */
    private int runTests(List<TestRunner> testRunners, List<TestCategory> testCategories)
    {
    	for (int i = 0; i < testCategories.size(); i++)
        {
            // So because these category lists SHOULD be identical (if you're
            // running
            // both JSoar and CSoar), I can get away with doing this only for
            // one of the categories. If there is an issue this will show me
            // that bug anyways
            // - ALT

            TestCategory category = testCategories.get(i);
            
            if (categoriesToRun != null && categoriesToRun.size() != 0)
            {
                if (categoriesToRun.contains(category.getCategoryName()) == false)
                    continue;
            }

            out.println("Starting " + category.getCategoryName() + ": \n");
            out.flush();

            List<Test> tests = null;

            if (jsoarEnabled)
                tests = jsoarTests;
            else
                tests = csoarTests;

            for (int j = 0; j < tests.size(); j++)
            {
                // Same thing as the above comment
                // - ALT
                
                if (testsToRun != null && testsToRun.size() != 0)
                {
                    if (testsToRun.contains(tests.get(j).getTestName()) == false)
                        continue;
                }
                
                if (category.containsTest(tests.get(j)) == false)
                        continue;

                out.println("Starting Test: " + tests.get(j).getTestName());
                out.flush();
                
                //Construct a table for the data
                
                Table table = new Table();
                
                for (int k = 0;k < 14;k++)
                {
                    Row row = new Row();
                    
                    switch (k)
                    {
                    case 0:
                        row.add(new Cell(tests.get(j).getTestName()));
                        row.add(new Cell("JSoar"));
                        row.add(new Cell("CSoar " + csoarTestFactory.getLabel()));
                        break;
                    case 1:
                        row.add(new Cell("Total CPU Time (s)"));
                        break;
                    case 2:
                        row.add(new Cell("Average CPU Time Per Run (s)"));
                        break;
                    case 3:
                        row.add(new Cell("Median CPU Time Per Run (s)"));
                        break;
                    case 4:
                        row.add(new Cell("Total Kernel Time (s)"));
                        break;
                    case 5:
                        row.add(new Cell("Average Kernel Time Per Run (s)"));
                        break;
                    case 6:
                        row.add(new Cell("Median Kernel Time Per Run (s)"));
                        break;
                    case 7:
                        row.add(new Cell("Decision Cycles Run For"));
                        break;
                    case 8:
                        row.add(new Cell("Average Decision Cycles Per Run"));
                        break;
                    case 9:
                        row.add(new Cell("Median Decision Cycles Per Run"));
                        break;
                    case 10:
                        row.add(new Cell("Memory Used (M)"));
                        break;
                    case 11:
                        row.add(new Cell("Average Memory Used Per Run (M)"));
                        break;
                    case 12:
                        row.add(new Cell("Median Memory Used Per Run (M)"));
                        break;
                    case 13:
                        row.add(new Cell("Memory Deviation from Average (M)"));
                        break;
                    }
                    
                    table.addRow(row);
                }

                // Run JSoar First
                if (jsoarEnabled)
                {
                    out.println("JSoar: ");
                    out.flush();

                    Test jsoarTest = jsoarTests.get(j);

                    TestRunner jsoarTestRunner = new TestRunner(jsoarTest, out);
                    try
                    {
                        jsoarTestRunner.runTestsForAverage(runCount, warmUpCount, seed);
                    }
                    catch (SoarException e)
                    {
                        out.println("Failed with a Soar Exception: " + e.getMessage());
                        return EXIT_FAILURE;
                    }
                    
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getTotalCPUTime()).toString(),          2-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getAverageCPUTime()).toString(),        3-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getMedianCPUTime()).toString(),         4-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getTotalKernelTime()).toString(),       5-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getAverageKernelTime()).toString(),     6-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getMedianKernelTime()).toString(),      7-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getTotalDecisionCycles()).toString(),   8-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getAverageDecisionCycles()).toString(), 9-1,   2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getMedianDecisionCycles()).toString(),  10-1,  2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getTotalMemoryLoad()/ 1000.0 / 1000.0).toString(),       11-1,  2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getAverageMemoryLoad()/ 1000.0 / 1000.0).toString(),     12-1,  2-1);
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getMedianMemoryLoad()/ 1000.0 / 1000.0).toString(),      13-1,  2-1);                    
                    table.setOrAddValueAtLocation(new Double(jsoarTestRunner.getMemoryLoadDeviation()/ 1000.0 / 1000.0).toString(),   14-1,  2-1);

                    testRunners.add(jsoarTestRunner);
                }

                // Run CSoar Second
                if (csoarEnabled)
                {
                    Test csoarTest = csoarTests.get(j);
                    out.println("CSoar " + csoarTestFactory.getLabel() + ": ");
                    out.flush();

                    TestRunner csoarTestRunner = new TestRunner(csoarTest, out);
                    try
                    {
                        csoarTestRunner.runTestsForAverage(runCount, 0, seed);
                    }
                    catch (SoarException e)
                    {
                        out.println("Failed with a Soar Exception: " + e.getMessage());
                        return EXIT_FAILURE;
                    }
                    
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getTotalCPUTime()).toString(),          2-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getAverageCPUTime()).toString(),        3-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getMedianCPUTime()).toString(),         4-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getTotalKernelTime()).toString(),       5-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getAverageKernelTime()).toString(),     6-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getMedianKernelTime()).toString(),      7-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getTotalDecisionCycles()).toString(),   8-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getAverageDecisionCycles()).toString(), 9-1,   3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getMedianDecisionCycles()).toString(),  10-1,  3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getTotalMemoryLoad() / 1000.0 / 1000.0).toString(),       11-1,  3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getAverageMemoryLoad() / 1000.0 / 1000.0).toString(),     12-1,  3-1);
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getMedianMemoryLoad() / 1000.0 / 1000.0).toString(),      13-1,  3-1);                    
                    table.setOrAddValueAtLocation(new Double(csoarTestRunner.getMemoryLoadDeviation() / 1000.0 / 1000.0).toString(),   14-1,  3-1);

                    testRunners.add(csoarTestRunner);
                }
                
                table.writeToWriter(out);
                
                if (csvDirectory.length() != 0)
                {
                    File outDirectory = new File(csvDirectory);
                    
                    if (!outDirectory.exists())
                    {
                    	outDirectory.mkdirs();
                    }
                    
                    String testNameWithoutSpaces = tests.get(j).getTestName().replaceAll("\\s+", "-");
                    
                    table.writeToCSV(csvDirectory + "/" + testNameWithoutSpaces + ".txt");
                    table.writeToCSV(csvDirectory + "/" + SUMMARY_FILE_NAME, true);
                }
                
                out.print("\n");
            }
        }
    	
    	return NON_EXIT;
    }
    
    /**
     * 
     * @param testRunners The runners to output
     * @return Whether outputing was successful
     */
    private int outputTestResults(List<TestRunner> testRunners)
    {
    	double totalTimeTaken = 0.0;
        long totalDecisionCycles = 0;

        for (TestRunner testRunner : testRunners)
        {
            totalTimeTaken += testRunner.getTotalCPUTime();
            totalDecisionCycles += testRunner.getTotalDecisionCycles();
        }

        out.println("Total Time Taken: " + totalTimeTaken + "\n" +
                    "Total Decision Cycles Run For: " + totalDecisionCycles + "\n");

        out.flush();
        
        out.println("\n\nIndividual Run Results:\n");
        
        for (TestRunner testRunner : testRunners)
        {
            List<Double> cpuTimes = testRunner.getAllCPUTimes();
            List<Double> kernelTimes = testRunner.getAllKernelTimes();
            List<Integer> decisionCycles = testRunner.getAllDecisionCycles();
            List<Long> memoryLoads = testRunner.getAllMemoryLoads();
            
            if (runCount == 1)
            {
                continue;
            }
            
            for (int i = 0;i < runCount;i++)
            {
                Table table = new Table();
                
                for (int j = 0;j < 5;j++)
                {
                    Row row = new Row();
                    
                    switch (j)
                    {
                    case 0:
                        row.add(new Cell(testRunner.getTest().getTestName() + " - Run " + (i+1)));
                        row.add(new Cell(testRunner.getTest().getDisplayName()));
                        break;
                    case 1:
                        row.add(new Cell("CPU Time (s)"));
                        row.add(new Cell(new Double(cpuTimes.get(i)).toString()));
                        break;
                    case 2:
                        row.add(new Cell("Kernel Time (s)"));
                        row.add(new Cell(new Double(kernelTimes.get(i)).toString()));
                        break;
                    case 3:
                        row.add(new Cell("Decision Cycles Total"));
                        row.add(new Cell(new Double(decisionCycles.get(i)).toString()));
                        break;
                    case 4:
                        row.add(new Cell("Memory Load (M)"));
                        row.add(new Cell(new Double(memoryLoads.get(i) / 1000.0 / 1000.0 ).toString()));
                    }
                    
                    table.addRow(row);
                }

                table.writeToWriter(out);
                
                if (csvDirectory.length() != 0)
                {
                    File out = new File(csvDirectory);
                    
                    if (!out.exists())
                    {
                        out.mkdirs();
                    }
                                        
                    String testNameWithoutSpaces = testRunner.getTest().getTestName().replaceAll("\\s+", "-") + "-" + testRunner.getTest().getDisplayName() + "-Run" + (i+1);
                    
                    table.writeToCSV(csvDirectory + "/" + testNameWithoutSpaces + ".txt");
                }
            }
        }
        
        out.flush();
        
        return NON_EXIT;
    }
    
    /**
     * 
     * @param args
     * @return Whether performance testing was successful or not.
     */
    public int doPerformanceTesting(String[] args)
    {
        int optionsParseResult = parseOptions(args);
        
        if (optionsParseResult != NON_EXIT)
        {
        	return optionsParseResult;
        }

        // Delete summary file so we don't append to old results
        // but only do this if we're not running in separate JVMs
        // because otherwise we would overwrite the old results!
        if (!runTestsInSeparateJVMs)
        {
            File summaryFile = new File(csvDirectory + "/" + SUMMARY_FILE_NAME);
            summaryFile.delete();
        }
        
        out.println("Performance Testing - Starting Tests\n");
        out.flush();
        
        List<Test> tests = null;
        List<TestCategory> testCategories = null;
        
        if (jsoarEnabled)
        {
            tests = jsoarTests;
            testCategories = jsoarTestCategories;
        }
        else
        {
            tests = csoarTests;
            testCategories = csoarTestCategories;
        }
        
        if (tests.size() > 1 && runTestsInSeparateJVMs)
        {
            return runTestsInChildrenJVMs(tests, testCategories);
        }
        else
        {
            return runTestsWithoutChildJVMs(tests, testCategories);
        }
    }

    /**
     * 
     * @param test The test to use for checking
     * @param testCategories The categories to check against
     * @return The category of the test.  Will return "Uncategorized Tests" if it didn't find any category.
     */
    private static String getTestCategoryForTest(Test test, List<TestCategory> testCategories)
    {
        for (TestCategory category : testCategories)
        {
            if (category.containsTest(test))
            {
                return category.getCategoryName();
            }
        }
        
        return "Uncategorized Tests";
    }
    
    /**
     * 
     * @param tests All the tests
     * @param testCategories All the categories for tests
     * @return Whether running the tests without child JVMs was successful or not
     */
    private int runTestsWithoutChildJVMs(List<Test> tests, List<TestCategory> testCategories)
    {
        List<TestRunner> testRunners = new ArrayList<TestRunner>();

        int runTestsResult = runTests(testRunners, testCategories);
        
        if (runTestsResult != NON_EXIT)
        {
            return runTestsResult;
        }

        out.println("Performance Testing - Done");

        int outputTestsResult = outputTestResults(testRunners);
        
        if (outputTestsResult != NON_EXIT)
        {
            return outputTestsResult;
        }
        
        return EXIT_SUCCESS;
    }
    
    /**
     * 
     * @param tests All the tests
     * @param testCategories The categories of all the tests
     * @return Whether running the tests in child JVMs was successful
     */
    private int runTestsInChildrenJVMs(List<Test> tests, List<TestCategory> testCategories)
    {
     // Since we have more than one test to run, spawn a separate JVM for each run.
        if (categoriesToRun == null)
        {
            categoriesToRun = new ArrayList<String>();
        }
        
        if (testsToRun == null)
        {
            testsToRun = new ArrayList<String>();
        }
        
        if (categoriesToRun.size() == 0 &&
            testsToRun.size() == 0)
        {
            for (TestCategory category : testCategories)
            {
                categoriesToRun.add(category.getCategoryName());
            }
        }
        
        for (int i = 0; i < testCategories.size(); i++)
        {
            // So because these category lists SHOULD be identical (if you're
            // running
            // both JSoar and CSoar), I can get away with doing this only for
            // one of the categories. If there is an issue this will show me
            // that bug anyways
            // - ALT

            TestCategory category = testCategories.get(i);
            
            if (categoriesToRun != null && categoriesToRun.size() != 0)
            {
                if (categoriesToRun.contains(category.getCategoryName()) == false)
                    continue;
            }

            for (int j = 0; j < tests.size(); j++)
            {
                // Same thing as the above comment
                // - ALT
                
                if (testsToRun != null && testsToRun.size() != 0)
                {
                    if (testsToRun.contains(tests.get(j).getTestName()) == false)
                        continue;
                }
                
                if (category.containsTest(tests.get(j)) == false)
                        continue;
                
                if (jsoarEnabled)
                {
                    Test test = jsoarTests.get(j);
                    
                    spawnChildJVMForTest(test, true);
                }
                
                if (csoarEnabled)
                {
                    Test test = csoarTests.get(j);
                    
                    spawnChildJVMForTest(test, false);
                }
            }
        }                    
        
        out.println("Performance Testing - Done");
        
        return EXIT_SUCCESS;
    }
    
    /**
     * Spawns a child JVM for the test and waits for it to exit.
     * 
     * @param test The test to run
     * @param jsoar Whether this is a JSoar or CSoar test
     * @return Whether the run was successful or not
     */
    private int spawnChildJVMForTest(Test test, boolean jsoar)
    {
        // Arguments to the process builder including the command to run
        ArrayList<String> arguments = new ArrayList<String>();
        
        URL baseURL = PerformanceTesting.class.getProtectionDomain().getCodeSource().getLocation();
        String jarPath = null;
        try
        {
            // Get the directory for the jar file or class path
            jarPath = new File(baseURL.toURI().getPath()).getCanonicalPath();
        }
        catch (IOException | URISyntaxException e1)
        {
            throw new AssertionError(e1);
        }
        
        // Replaces windows style paths with unix.
        jarPath = jarPath.replace("\\","/");
                
        if (jarPath.endsWith(".jar") != true)
        {
            // We're not in a jar so most likely eclipse
            // Set up class path
            
            // This is going to make the assumption that we're in eclipse
            // and so it will assume a repository structure and if this
            // changes or is wrong, this code will break and this will fail.
            // - ALT
            
            // Add the performance testing framework class path
            String originalPath = jarPath;
            
            jarPath += ";";
            // Add the jsoar core class path
            jarPath += originalPath + "/../../jsoar-core/bin/;";
            
            // Add all the require libs from jsoar core
            File directory = new File(originalPath + "/../../jsoar-core/lib/");
            File[] listOfFiles = directory.listFiles();
            for (File file : listOfFiles)
            {
                if (file.isFile())
                {
                    String path = file.getPath();
                    
                    if (!path.endsWith(".jar"))
                    {
                        continue;
                    }
                    
                    path = path.replace("\\", "/");
                    
                    jarPath += path + ";";
                }
            }
            
            // Add all the required libs from the performance testing framework
            directory = new File(originalPath + "/../lib/");
            listOfFiles = directory.listFiles();
            for (File file : listOfFiles)
            {
                if (file.isFile())
                {
                    String path = file.getPath();
                    
                    if (!path.endsWith(".jar"))
                    {
                        continue;
                    }
                    
                    path = path.replace("\\", "/");
                    
                    jarPath += path + ";";
                }
            }
        }
        
        // Construct the array with the command and arguments
        // Use javaw for no console window spawning
        arguments.add("javaw");
        arguments.add("-classpath"); // Always use class path.  This will even work for jars.
        arguments.add("\"" + jarPath + "\"");
        arguments.add(PerformanceTesting.class.getCanonicalName()); // Get the class name to load
        arguments.add("--test");
        arguments.add(test.getTestFile());
        arguments.add("--output");
        arguments.add(csvDirectory);
        arguments.add("--kind");
        arguments.add("\"" + PerformanceTesting.getTestCategoryForTest(test, (jsoar ? jsoarTestCategories : csoarTestCategories)) + "\"");
        arguments.add("--warmup");
        arguments.add(new Integer(warmUpCount).toString());
        arguments.add("--" + (jsoar ? "j" : "") + "soar");
        
        // Run the process and get the exit code
        int exitCode = 0;
        try
        {
            out.print("Running '" + arguments.toString() + "'\n\n");
            out.flush();
            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
            
            // Redirect the output so we can see what is going on
            processBuilder.redirectError(Redirect.INHERIT);
            processBuilder.redirectOutput(Redirect.INHERIT);
            
            Process process = processBuilder.start();
            exitCode = process.waitFor();
        }
        catch (IOException e)
        {
            throw new AssertionError(e);
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
        
        if (exitCode != 0)
        {
            return EXIT_FAILURE_TEST;
        }
        else
        {
            return EXIT_SUCCESS;
        }
    }
}
