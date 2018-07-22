import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;

public class CmdWork implements Callable<Integer> {

  private final String cmd;
  private final List<String> args;
  private Map<String, String> env;

  //    private int returnCode = -1;
  private String output = null;
  private String error = null;

  private long responseTime = -1;

  public CmdWork(String cmd, List<String> args, Map<String, String> env) {
    this.cmd = cmd;
    this.args = args;
    this.env = env;
  }

  public String getCmd() {
    return this.cmd;
  }

  public List<String> getArgs() {
    return this.args;
  }

  public Map<String, String> getEnv() {
    return this.env;
  }

  public long getResponseTime() {
    return this.responseTime;
  }

  public String getOutput() {
    // System.out.println("CmdWork: output: " + this.output);
    return this.output;
  }

  public String getError() {
    return this.error;
  }

  public Integer call() throws Exception {

    long startTime = System.currentTimeMillis();
    Executor executor = new DefaultExecutor();
    executor.setExitValue(0);
    int exitValue = -1;
    CommandLine commandLine = new CommandLine(this.cmd);
    // Arguments
    String[] procEnv = EnvironmentUtils.toStrings(EnvironmentUtils.getProcEnvironment());
    for (int i = 0; i < procEnv.length; i++) {
	System.out.println("proEnv: " + procEnv[i]);
    }

    if (this.args != null) {
      Iterator<String> it = this.getArgs().iterator();
      while (it.hasNext()) {
	  String arg = it.next();
	  System.out.println("arg: " + arg);
        commandLine.addArguments(arg);
      }
    }
    System.out.println("cmd: " + this.cmd);
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

    ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);

    // set watchdog and starting synchronous child process
    executor.setWatchdog(watchdog);

    // create string output for executor and add as pump handler
    ProcessStringOutput processOutput = new ProcessStringOutput();
    ProcessStringOutput errorOutput = new ProcessStringOutput();
    executor.setStreamHandler(new PumpStreamHandler(processOutput, errorOutput));
    executor.setWorkingDirectory(new File(System.getProperty("java.io.tmpdir")));

    
    if (this.getEnv() != null) {
	System.out.println("key: " + this.env.toString());
	//exitValue = executor.execute(commandLine, this.env);
	executor.execute(commandLine, this.env, resultHandler);
    } else {
	//exitValue = executor.execute(commandLine);
	executor.execute(commandLine, resultHandler);
    }
    resultHandler.waitFor(60000);
    exitValue = resultHandler.getExitValue();

    this.output = processOutput.getOutput();
    this.error = errorOutput.getOutput();

    long endTime = System.currentTimeMillis();
    this.responseTime = (endTime - startTime);

    return Integer.valueOf(exitValue);
  }
  /*
     public Integer call() throws Exception {

  long startTime = System.currentTimeMillis();
  Process process = null;
  try {

      ProcessBuilder pb = new ProcessBuilder(this.args);
      Map<String, String> processEnv = pb.environment();
      if (this.env != null) {
  	processEnv.putAll(this.env);
      }
      //pb.redirectOutput(Redirect.PIPE);
      //pb.redirectError(Redirect.INHERIT);
      //pb.redirectInput(Redirect.PIPE);
      //assert pb.redirectInput() == Redirect.PIPE;
      //assert process.getInputStream().read() == -1;
      System.out.println("tmpdir: " + System.getProperty("java.io.tmpdir"));
      pb.inheritIO();

      //pb.directory(new File(System.getProperty("java.io.tmpdir")));
      //File log = new File("log");
      //pb.redirectErrorStream(true);
      //pb.redirectOutput(Redirect.appendTo(log));

      process = pb.start();
      boolean pStatus = process.waitFor(5, TimeUnit.SECONDS);

      this.returnCode = process.exitValue();

      long endTime = System.currentTimeMillis();
      this.responseTime = (endTime - startTime);
      System.out.println("this.cmd: " + this.responseTime + " ms" + " status: " + pStatus);

      this.output = output(process.getInputStream());
      System.out.println("CmdWork direct output: " + output);
      this.error = output(process.getErrorStream());
      //this.output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
      //this.error = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);


  } catch (Exception ex) {
      ex.printStackTrace();
      process.destroy();
      process.waitFor(); // wait for the process to terminate
  }

  return new Integer(this.returnCode);
     }

     private String output(InputStream inputStream) throws IOException {

         StringBuilder sb = new StringBuilder();
         BufferedReader br = null;

         try {
             br = new BufferedReader(new InputStreamReader(inputStream));
             String line = null;

             while ((line = br.readLine()) != null) {
                 sb.append(line + System.getProperty("line.separator"));
             }

         } finally {
             br.close();
         }
         return sb.toString();
     }
     */
}
